package com.example.demo.service;

import com.example.demo.client.MercadoLivreOrderClient;
import com.example.demo.config.MercadoLivreProperties;
import com.example.demo.domain.CriterioDataEnvio;
import com.example.demo.domain.EnvioMercadoLivre;
import com.example.demo.domain.ItemVenda;
import com.example.demo.dto.ml.MlOrderDTO;
import com.example.demo.dto.ml.MlOrderItemDTO;
import com.example.demo.dto.ml.MlOrderSearchResponse;
import com.example.demo.dto.ml.MlShipmentCostsDTO;
import com.example.demo.dto.ml.MlShipmentDTO;
import com.example.demo.dto.ml.MlShipmentHistoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Passo 4 do roteiro: descobre os ENVIOS de um dia, identifica quais sao
 * FLEX e devolve o modelo de dominio (EnvioMercadoLivre com seus
 * ItemVenda), pronto para o mapeamento de SKU e a consolidacao.
 *
 * "Envios do dia" depende do CriterioDataEnvio:
 *
 *  - CRIACAO_PEDIDO: pedidos pagos criados no dia, agrupados por envio.
 *  - SAIDA_ENVIO: como a API nao permite buscar envios por data de saida,
 *    busca pedidos criados na janela [dia - diasRetroativos, fim do dia],
 *    agrupa por envio e le o historico de cada um, ficando so com os que
 *    sairam no dia.
 *
 * Nos dois casos os pedidos sao agrupados por envio: um carrinho gera varios
 * pedidos no MESMO envio, e o desconto FLEX e uma vez por envio.
 */
@Service
public class MercadoLivreOrderService {

    private static final Logger log = LoggerFactory.getLogger(MercadoLivreOrderService.class);

    private static final int TAMANHO_PAGINA = 50;
    private static final String STATUS_PAGO = "paid";

    private final MercadoLivreOrderClient orderClient;
    private final MercadoLivreProperties properties;

    public MercadoLivreOrderService(MercadoLivreOrderClient orderClient, MercadoLivreProperties properties) {
        this.orderClient = orderClient;
        this.properties = properties;
    }

    /** Usa o criterio configurado (ML_CRITERIO_DATA, padrao CRIACAO_PEDIDO). */
    public List<EnvioMercadoLivre> buscarEnviosDoDia(LocalDate dia) {
        return buscarEnviosDoDia(dia, properties.criterioDataOuPadrao());
    }

    public List<EnvioMercadoLivre> buscarEnviosDoDia(LocalDate dia, CriterioDataEnvio criterio) {
        log.info("Buscando envios de {} (criterio: {})", dia, criterio);
        return switch (criterio) {
            case SAIDA_ENVIO -> buscarPorSaidaDoEnvio(dia);
            case ENVIOS_DE_HOJE -> buscarEnviosDeHoje(dia);
            case CRIACAO_PEDIDO -> buscarPorCriacaoDoPedido(dia);
        };
    }

    // ------------------------------------------------------------------
    // CRIACAO_PEDIDO
    // ------------------------------------------------------------------

    private List<EnvioMercadoLivre> buscarPorCriacaoDoPedido(LocalDate dia) {
        List<MlOrderDTO> pedidos = buscarTodosOsPedidos(inicioDoDia(dia), fimDoDia(dia));
        log.info("{} pedido(s) criado(s) em {}", pedidos.size(), dia);

        Map<Long, List<MlOrderDTO>> pedidosPorEnvio = agruparPedidosPagosPorEnvio(pedidos);

        List<EnvioMercadoLivre> envios = new ArrayList<>();
        for (Map.Entry<Long, List<MlOrderDTO>> entrada : pedidosPorEnvio.entrySet()) {
            // Se a consulta falhar, deixamos a excecao subir: assumir "nao
            // FLEX" em silencio faria o desconto de R$13 sumir sem ninguem ver.
            MlShipmentDTO envio = orderClient.buscarEnvio(entrada.getKey());
            boolean flex = envio != null && envio.isFlex();

            envios.add(montarEnvio(entrada.getKey(), entrada.getValue(), flex, null));
        }

        registrarResumo(envios, dia);
        return envios;
    }

    // ------------------------------------------------------------------
    // SAIDA_ENVIO
    // ------------------------------------------------------------------

    private List<EnvioMercadoLivre> buscarPorSaidaDoEnvio(LocalDate dia) {
        int diasRetroativos = properties.diasRetroativosOuPadrao();
        OffsetDateTime inicio = inicioDoDia(dia.minusDays(diasRetroativos));

        List<MlOrderDTO> pedidos = buscarTodosOsPedidos(inicio, fimDoDia(dia));
        log.info("{} pedido(s) criado(s) entre {} e {}", pedidos.size(), inicio.toLocalDate(), dia);

        Map<Long, List<MlOrderDTO>> pedidosPorEnvio = agruparPedidosPagosPorEnvio(pedidos);
        log.info("{} envio(s) distinto(s) a verificar", pedidosPorEnvio.size());

        List<EnvioMercadoLivre> envios = new ArrayList<>();
        for (Map.Entry<Long, List<MlOrderDTO>> entrada : pedidosPorEnvio.entrySet()) {
            Long shippingId = entrada.getKey();

            // Falhas sobem de proposito: pular o envio em silencio faria uma
            // venda (ou o desconto FLEX) sumir sem ninguem ver.
            MlShipmentDTO envio = orderClient.buscarEnvio(shippingId);
            if (envio == null || !RegraSaidaEnvio.podeTerSaido(envio.status())) {
                continue;
            }

            List<MlShipmentHistoryDTO> historico = orderClient.buscarHistoricoEnvio(shippingId);
            Optional<OffsetDateTime> saida = RegraSaidaEnvio.dataDeSaida(historico);
            if (saida.isEmpty() || !RegraSaidaEnvio.saiuNoDia(saida.get(), dia)) {
                continue;
            }

            envios.add(montarEnvio(shippingId, entrada.getValue(), envio.isFlex(), saida.get()));
        }

        registrarResumo(envios, dia);
        return envios;
    }

    // ------------------------------------------------------------------
    // ENVIOS_DE_HOJE
    // ------------------------------------------------------------------

    /** Quantos dias para tras olhamos pedidos: um envio pode esperar dias para ser despachado. */
    private static final int JANELA_ENVIOS_PENDENTES_DIAS = 30;

    /**
     * Equivale a lista "Envios de hoje" do painel do ML: envios prontos para
     * despachar (status ready_to_ship), sem os agendados para dias futuros
     * (substatus buffered). A data do PEDIDO nao importa. Quando o envio e
     * despachado, ele sai da lista, entao e preciso rodar antes disso.
     */
    private List<EnvioMercadoLivre> buscarEnviosDeHoje(LocalDate dia) {
        OffsetDateTime inicio = inicioDoDia(dia.minusDays(JANELA_ENVIOS_PENDENTES_DIAS));
        List<MlOrderDTO> pedidos = buscarTodosOsPedidos(inicio, fimDoDia(dia));
        Map<Long, List<MlOrderDTO>> pedidosPorEnvio = agruparPedidosPagosPorEnvio(pedidos);
        log.info("{} envio(s) distinto(s) a verificar (pedidos dos ultimos {} dias)",
                pedidosPorEnvio.size(), JANELA_ENVIOS_PENDENTES_DIAS);

        List<EnvioMercadoLivre> envios = new ArrayList<>();
        for (Map.Entry<Long, List<MlOrderDTO>> entrada : pedidosPorEnvio.entrySet()) {
            // falha de consulta sobe de proposito: pular em silencio esconderia uma venda
            MlShipmentDTO envio = orderClient.buscarEnvio(entrada.getKey());
            if (envio == null || !RegraEnviosDeHoje.estaNaListaDeHoje(envio.status(), envio.substatus())) {
                continue;
            }
            EnvioMercadoLivre montado = montarEnvio(entrada.getKey(), entrada.getValue(), envio.isFlex(), null);
            montado.getAvisos().add("Situacao do envio no ML: " + envio.status()
                    + (envio.substatus() != null ? " / " + envio.substatus() : ""));
            envios.add(montado);
        }

        registrarResumo(envios, dia);
        return envios;
    }

    // ------------------------------------------------------------------
    // Comum
    // ------------------------------------------------------------------

    private static OffsetDateTime inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay().atOffset(RegraSaidaEnvio.FUSO_BRASIL);
    }

    private static OffsetDateTime fimDoDia(LocalDate dia) {
        return dia.plusDays(1).atStartOfDay().atOffset(RegraSaidaEnvio.FUSO_BRASIL).minusNanos(1_000_000);
    }

    private void registrarResumo(List<EnvioMercadoLivre> envios, LocalDate dia) {
        long totalFlex = envios.stream().filter(EnvioMercadoLivre::isFlex).count();
        log.info("{} envio(s) em {}, sendo {} FLEX", envios.size(), dia, totalFlex);
    }

    /** Percorre todas as paginas de /orders/search ate acabar. */
    private List<MlOrderDTO> buscarTodosOsPedidos(OffsetDateTime inicio, OffsetDateTime fim) {
        List<MlOrderDTO> todos = new ArrayList<>();
        int offset = 0;

        while (true) {
            MlOrderSearchResponse pagina = orderClient.buscarPedidos(inicio, fim, offset, TAMANHO_PAGINA);
            if (pagina == null || pagina.results() == null || pagina.results().isEmpty()) {
                break;
            }
            todos.addAll(pagina.results());
            offset += TAMANHO_PAGINA;

            int total = pagina.paging() != null ? pagina.paging().total() : todos.size();
            if (offset >= total) {
                break;
            }
        }
        return todos;
    }

    /**
     * Junta os pedidos PAGOS que compartilham o mesmo envio (carrinho).
     * Pedidos cancelados/pendentes ou sem envio nao entram.
     */
    private Map<Long, List<MlOrderDTO>> agruparPedidosPagosPorEnvio(List<MlOrderDTO> pedidos) {
        Map<Long, List<MlOrderDTO>> porEnvio = new LinkedHashMap<>();
        for (MlOrderDTO pedido : pedidos) {
            if (!STATUS_PAGO.equals(pedido.status())) {
                log.info("Pedido {} ignorado (status={})", pedido.id(), pedido.status());
                continue;
            }
            Long shippingId = pedido.shipping() != null ? pedido.shipping().id() : null;
            if (shippingId == null) {
                log.info("Pedido {} ignorado (sem envio)", pedido.id());
                continue;
            }
            porEnvio.computeIfAbsent(shippingId, id -> new ArrayList<>()).add(pedido);
        }
        return porEnvio;
    }

    private EnvioMercadoLivre montarEnvio(Long shippingId, List<MlOrderDTO> pedidos,
                                          boolean flex, OffsetDateTime saida) {
        List<Long> orderIds = new ArrayList<>();
        List<ItemVenda> itens = new ArrayList<>();

        for (MlOrderDTO pedido : pedidos) {
            orderIds.add(pedido.id());
            if (pedido.orderItems() == null) {
                continue;
            }
            for (MlOrderItemDTO oi : pedido.orderItems()) {
                String sku = oi.item() != null ? oi.item().skuEfetivo() : null;
                String titulo = oi.item() != null ? oi.item().title() : null;

                if (sku == null || sku.isBlank()) {
                    log.warn("Pedido {}: item '{}' sem SKU cadastrado no anuncio", pedido.id(), titulo);
                }
                itens.add(new ItemVenda(sku, titulo, oi.quantity(), oi.unitPrice()));
            }
        }

        BigDecimal tarifa = CalculoRecebimento.tarifaDeVenda(pedidos);
        List<String> avisos = new ArrayList<>();
        BigDecimal frete = buscarCustoFreteDoVendedor(shippingId, avisos);

        return new EnvioMercadoLivre(shippingId, orderIds, flex, saida, itens, tarifa, frete, avisos);
    }

    /**
     * Frete que o vendedor paga no envio (GET /shipments/{id}/costs). Se o ML
     * nao tiver essa informacao para o envio (404 ou vendedor ausente), usa 0
     * mas registra um aviso visivel no resultado -- nunca em silencio. Outros
     * erros (401, 403, 500...) sobem de proposito.
     */
    private BigDecimal buscarCustoFreteDoVendedor(Long shippingId, List<String> avisos) {
        Long sellerId = sellerIdConfigurado();
        try {
            MlShipmentCostsDTO custos = orderClient.buscarCustosEnvio(shippingId);
            Optional<BigDecimal> custo = CalculoRecebimento.custoFreteDoVendedor(custos, sellerId);
            if (custo.isPresent()) {
                return custo.get();
            }
            String aviso = "Custo de frete do vendedor nao consta nos custos do envio; considerado 0.";
            log.warn("Envio {}: {}", shippingId, aviso);
            avisos.add(aviso);
        } catch (WebClientResponseException.NotFound e) {
            String aviso = "Custos do envio indisponiveis (404); frete considerado 0.";
            log.warn("Envio {}: {}", shippingId, aviso);
            avisos.add(aviso);
        }
        return BigDecimal.ZERO;
    }

    private Long sellerIdConfigurado() {
        String texto = properties.sellerId();
        try {
            return Long.valueOf(texto.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "ML_SELLER_ID ausente ou invalido ('" + texto + "'): e necessario para calcular o frete do vendedor.", e);
        }
    }
}
