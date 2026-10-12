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
 * Passos 6 e 7: consolida os envios do dia numa UNICA venda balcao do VHSYS
 * (cliente "MERCADO LIVRE") e lanca as pecas com o valor recebido rateado.
 *
 * Seguranca contra duplicidade:
 *  - cada envio lancado fica registrado (EnvioEnviadoVhsys, shippingId unico);
 *    rodar de novo so lanca envios NOVOS;
 *  - a venda do dia (id_frente) fica registrada; envios novos do mesmo dia
 *    entram na MESMA venda;
 *  - se houver SKU sem produto no VHSYS, NADA e enviado.
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

        Long idFrenteExistente = vendaRepository.findByDia(dia).map(VendaVhsysDia::getIdFrente).orElse(null);

        if (novos.isEmpty()) {
            return new Resultado(dia, Situacao.NADA_A_ENVIAR, idFrenteExistente,
                    List.of(), List.of(), List.of(), BigDecimal.ZERO, List.of());
        }

        MapeamentoSkuService.Resultado mapeamento = mapeamentoSkuService.mapear(novos);
        List<String> avisos = novos.stream().flatMap(e -> e.getAvisos().stream()).toList();
        List<Long> shippingIds = novos.stream().map(EnvioMercadoLivre::getShippingId).toList();
        if (!mapeamento.completo()) {
            return new Resultado(dia, Situacao.BLOQUEADO_SKU_PENDENTE, idFrenteExistente,
                    shippingIds, mapeamento.skusPendentes(), List.of(), BigDecimal.ZERO, avisos);
        }

        List<VhsysItemVendaBalcaoDTO> itens = new ArrayList<>();
        BigDecimal totalLiquido = BigDecimal.ZERO;
        for (EnvioMercadoLivre envio : novos) {
            BigDecimal somaEnvio = BigDecimal.ZERO;
            for (RateioValorRecebido.LinhaRateada linha : RateioValorRecebido.ratear(envio)) {
                itens.add(new VhsysItemVendaBalcaoDTO(linha.quantidade(),
                        linha.item().getProdutoIdVhsys(), linha.item().getCodProdutoVhsys(),
                        linha.valorUnitario(), descricaoDoItem(linha.item())));
                somaEnvio = somaEnvio.add(linha.valorTotal());
            }
            if (somaEnvio.compareTo(envio.getValorLiquido()) != 0) {
                throw new IllegalStateException("Rateio do envio " + envio.getShippingId()
                        + " nao fechou: linhas=" + somaEnvio + " liquido=" + envio.getValorLiquido());
            }
            totalLiquido = totalLiquido.add(somaEnvio);
        }

        if (!confirmar) {
            return new Resultado(dia, Situacao.PREVIA, idFrenteExistente,
                    shippingIds, List.of(), itens, totalLiquido, avisos);
        }

        Long idFrente = idFrenteExistente;
        if (idFrente == null) {
            idFrente = vhsysClient.criarVendaBalcao(new VhsysVendaBalcaoRequestDTO(
                    NOME_CLIENTE, STATUS_PEDIDO, "Vendas Mercado Livre de " + dia + " (integracao)"));
            vendaRepository.save(new VendaVhsysDia(dia, idFrente)); // guarda ja: se os itens falharem, nao cria outra venda
            log.info("Venda balcao criada no VHSYS: id_frente={} dia={}", idFrente, dia);
        }

        vhsysClient.adicionarProdutosVendaBalcao(idFrente, itens);

        final Long idFrenteFinal = idFrente;
        envioRepository.saveAll(novos.stream()
                .map(e -> new EnvioEnviadoVhsys(e.getShippingId(), dia, idFrenteFinal, e.getValorLiquido()))
                .toList());
        log.info("{} envio(s) lancados na venda id_frente={} total={}", novos.size(), idFrente, totalLiquido);

        return new Resultado(dia, Situacao.ENVIADO, idFrente, shippingIds, List.of(), itens, totalLiquido, avisos);
    }

    /** Usa o nome do produto CADASTRADO no VHSYS; so cai no titulo do ML se o cadastro nao tiver nome. */
    private static String descricaoDoItem(com.example.demo.domain.ItemVenda item) {
        String desc = item.getDescProdutoVhsys();
        return desc != null && !desc.isBlank() ? desc : item.getTitulo();
    }
}
