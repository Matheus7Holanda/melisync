package com.example.demo.service;

import com.example.demo.client.VhsysClient;
import com.example.demo.domain.CriterioDataEnvio;
import com.example.demo.domain.EnvioEnviadoVhsys;
import com.example.demo.domain.EnvioMercadoLivre;
import com.example.demo.domain.VendaVhsysDia;
import com.example.demo.dto.vhsys.VhsysItemVendaBalcaoDTO;
import com.example.demo.dto.vhsys.VhsysVendaBalcaoRequestDTO;
import com.example.demo.repository.EnvioEnviadoVhsysRepository;
import com.example.demo.repository.VendaVhsysDiaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Passos 6 e 7: lanca os envios do Mercado Livre numa venda balcao do VHSYS
 * (cliente "MERCADO LIVRE"), com o valor recebido rateado por peca.
 *
 * Qual venda recebe os envios: a ULTIMA venda criada pela integracao, desde que
 * ainda esteja "Em Aberto" no VHSYS (consulta na hora). Se ela ja foi concluida,
 * cancelada ou excluida, abre uma venda nova. O estoque NAO e baixado aqui:
 * baixa quando voce conclui a venda no PDV do VHSYS.
 *
 * Contra duplicidade: cada envio lancado fica registrado (shippingId unico);
 * rodar de novo so lanca envios NOVOS. Com SKU sem produto no VHSYS, nada e enviado.
 * Produtos iguais sao juntos numa linha so, somando a quantidade.
 */
@Service
public class EnvioVhsysService {

    private static final Logger log = LoggerFactory.getLogger(EnvioVhsysService.class);

    public static final String NOME_CLIENTE = "MERCADO LIVRE";
    public static final String STATUS_PEDIDO = "Em Aberto";

    public enum Situacao { PREVIA, ENVIADO, NADA_A_ENVIAR, BLOQUEADO_SKU_PENDENTE }

    public record Resultado(
            LocalDate dia,
            Situacao situacao,
            Long idFrente,
            List<Long> shippingIds,
            List<String> skusPendentes,
            List<VhsysItemVendaBalcaoDTO> itens,
            BigDecimal totalLiquido,
            List<String> avisos
    ) {
    }

    private final MercadoLivreOrderService orderService;
    private final MapeamentoSkuService mapeamentoSkuService;
    private final VhsysClient vhsysClient;
    private final VendaVhsysDiaRepository vendaRepository;
    private final EnvioEnviadoVhsysRepository envioRepository;

    public EnvioVhsysService(MercadoLivreOrderService orderService,
                             MapeamentoSkuService mapeamentoSkuService,
                             VhsysClient vhsysClient,
                             VendaVhsysDiaRepository vendaRepository,
                             EnvioEnviadoVhsysRepository envioRepository) {
        this.orderService = orderService;
        this.mapeamentoSkuService = mapeamentoSkuService;
        this.vhsysClient = vhsysClient;
        this.vendaRepository = vendaRepository;
        this.envioRepository = envioRepository;
    }

    /** confirmar=false: so mostra o que SERIA enviado (nada vai ao VHSYS). */
    public synchronized Resultado sincronizar(LocalDate dia, CriterioDataEnvio criterio, boolean confirmar) {
        List<EnvioMercadoLivre> todos = criterio != null
                ? orderService.buscarEnviosDoDia(dia, criterio)
                : orderService.buscarEnviosDoDia(dia);

        Set<Long> jaEnviados = envioRepository
                .findByShippingIdIn(todos.stream().map(EnvioMercadoLivre::getShippingId).toList())
                .stream().map(EnvioEnviadoVhsys::getShippingId).collect(Collectors.toSet());
        List<EnvioMercadoLivre> novos = todos.stream()
                .filter(e -> !jaEnviados.contains(e.getShippingId()))
                .toList();

        if (novos.isEmpty()) {
            return new Resultado(dia, Situacao.NADA_A_ENVIAR, null,
                    List.of(), List.of(), List.of(), BigDecimal.ZERO, List.of());
        }

        // venda que vai receber os envios: a ultima da integracao, se ainda "Em Aberto" no VHSYS
        Long idFrenteAberta = vendaRepository.findFirstByOrderByCriadaEmDesc()
                .map(VendaVhsysDia::getIdFrente)
                .filter(vhsysClient::vendaBalcaoEstaAberta)
                .orElse(null);

        MapeamentoSkuService.Resultado mapeamento = mapeamentoSkuService.mapear(novos);
        List<String> avisos = novos.stream().flatMap(e -> e.getAvisos().stream()).toList();
        List<Long> shippingIds = novos.stream().map(EnvioMercadoLivre::getShippingId).toList();
        if (!mapeamento.completo()) {
            return new Resultado(dia, Situacao.BLOQUEADO_SKU_PENDENTE, idFrenteAberta,
                    shippingIds, mapeamento.skusPendentes(), List.of(), BigDecimal.ZERO, avisos);
        }

        List<RateioValorRecebido.LinhaRateada> linhasRateadas = new ArrayList<>();
        BigDecimal totalLiquido = BigDecimal.ZERO;
        for (EnvioMercadoLivre envio : novos) {
            List<RateioValorRecebido.LinhaRateada> doEnvio = RateioValorRecebido.ratear(envio);
            BigDecimal somaEnvio = doEnvio.stream()
                    .map(RateioValorRecebido.LinhaRateada::valorTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (somaEnvio.compareTo(envio.getValorLiquido()) != 0) {
                throw new IllegalStateException("Rateio do envio " + envio.getShippingId()
                        + " nao fechou: linhas=" + somaEnvio + " liquido=" + envio.getValorLiquido());
            }
            linhasRateadas.addAll(doEnvio);
            totalLiquido = totalLiquido.add(somaEnvio);
        }

        List<VhsysItemVendaBalcaoDTO> itens = AgrupadorDeLinhas.agrupar(linhasRateadas).stream()
                .map(l -> new VhsysItemVendaBalcaoDTO(l.quantidade(), l.idProduto(), l.codigo(),
                        l.valorUnitario(), l.descricao()))
                .toList();

        if (!confirmar) {
            return new Resultado(dia, Situacao.PREVIA, idFrenteAberta,
                    shippingIds, List.of(), itens, totalLiquido, avisos);
        }

        Long idFrente = idFrenteAberta;
        if (idFrente == null) {
            idFrente = vhsysClient.criarVendaBalcao(new VhsysVendaBalcaoRequestDTO(
                    NOME_CLIENTE, STATUS_PEDIDO, "Vendas Mercado Livre (integracao)"));
            registrarVenda(dia, idFrente); // guarda ja: se os itens falharem, nao cria outra venda
            log.info("Venda balcao criada no VHSYS: id_frente={}", idFrente);
        }

        vhsysClient.adicionarProdutosVendaBalcao(idFrente, itens);

        final Long idFrenteFinal = idFrente;
        envioRepository.saveAll(novos.stream()
                .map(e -> new EnvioEnviadoVhsys(e.getShippingId(), dia, idFrenteFinal, e.getValorLiquido()))
                .toList());
        log.info("{} envio(s) lancados na venda id_frente={} total={}", novos.size(), idFrente, totalLiquido);

        return new Resultado(dia, Situacao.ENVIADO, idFrente, shippingIds, List.of(), itens, totalLiquido, avisos);
    }

    private void registrarVenda(LocalDate dia, Long idFrente) {
        VendaVhsysDia existente = vendaRepository.findByDia(dia).orElse(null);
        if (existente != null) {
            existente.trocarVenda(idFrente);
            vendaRepository.save(existente);
        } else {
            vendaRepository.save(new VendaVhsysDia(dia, idFrente));
        }
    }
}
