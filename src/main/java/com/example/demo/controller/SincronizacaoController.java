package com.example.demo.controller;

import com.example.demo.domain.CriterioDataEnvio;
import com.example.demo.domain.EnvioMercadoLivre;
import com.example.demo.client.VhsysClient;
import com.example.demo.service.EnvioVhsysService;
import com.example.demo.service.MapeamentoSkuService;
import com.example.demo.service.MercadoLivreOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoint de consulta para testar o Passo 4 manualmente.
 *   GET http://localhost:8080/sincronizacao/pedidos-do-dia?data=2026-10-10
 *   GET .../sincronizacao/pedidos-do-dia?data=2026-10-10&criterio=SAIDA_ENVIO
 * - "data": sem ele, usa o dia de hoje.
 * - "criterio": CRIACAO_PEDIDO ou SAIDA_ENVIO; sem ele, usa o configurado
 *   em ML_CRITERIO_DATA (padrao CRIACAO_PEDIDO).
 *
 * ATENCAO: esse endpoint devolve dados de vendas. Com o ngrok ligado, a
 * aplicacao fica acessivel pela internet, entao o endpoint recusa
 * requisicoes que chegam pelo tunel (que trazem o header X-Forwarded-For).
 * Acesse sempre por http://localhost:8080.
 */
@RestController
public class SincronizacaoController {

    private final MercadoLivreOrderService orderService;

    private final MapeamentoSkuService mapeamentoSkuService;
    private final EnvioVhsysService envioVhsysService;
    private final VhsysClient vhsysClient;

    public SincronizacaoController(MercadoLivreOrderService orderService,
                                   MapeamentoSkuService mapeamentoSkuService,
                                   EnvioVhsysService envioVhsysService,
                                   VhsysClient vhsysClient) {
        this.vhsysClient = vhsysClient;
        this.orderService = orderService;
        this.mapeamentoSkuService = mapeamentoSkuService;
        this.envioVhsysService = envioVhsysService;
    }

    /**
     * Passo 6 (PREVIA): mostra o que seria lancado no VHSYS. Nao envia nada.
     *   GET http://localhost:8080/sincronizacao/enviar-vhsys?data=2026-10-11
     */
    @GetMapping("/sincronizacao/enviar-vhsys")
    public EnvioVhsysService.Resultado previa(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) CriterioDataEnvio criterio,
            HttpServletRequest request
    ) {
        apenasLocalhost(request);
        return envioVhsysService.sincronizar(data != null ? data : LocalDate.now(), criterio, false);
    }

    /**
     * Passo 7 (ENVIA DE VERDADE): cria/atualiza a venda balcao do dia no VHSYS.
     *   POST http://localhost:8080/sincronizacao/enviar-vhsys?data=2026-10-11
     */
    @PostMapping("/sincronizacao/enviar-vhsys")
    public EnvioVhsysService.Resultado enviar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) CriterioDataEnvio criterio,
            HttpServletRequest request
    ) {
        apenasLocalhost(request);
        return envioVhsysService.sincronizar(data != null ? data : LocalDate.now(), criterio, true);
    }

    /**
     * DIAGNOSTICO (somente leitura). Exemplos:
     *   /sincronizacao/vhsys-raw?caminho=/produtos?cod_produto=FJ2090K
     *   /sincronizacao/vhsys-raw?caminho=/vendas-balcao/123456/produtos
     */
    @GetMapping(value = "/sincronizacao/vhsys-raw", produces = "application/json")
    public String vhsysBruto(@RequestParam String caminho, HttpServletRequest request) {
        apenasLocalhost(request);
        return vhsysClient.consultaBruta(caminho);
    }

    private void apenasLocalhost(HttpServletRequest request) {
        if (request.getHeader("X-Forwarded-For") != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Endpoint disponivel apenas via localhost.");
        }
    }

    /**
     * Passo 5 (so consulta, nao envia nada ao VHSYS):
     *   GET http://localhost:8080/sincronizacao/mapeamento-sku?data=2026-10-11
     * Mostra quais SKUs do dia existem no VHSYS e quais estao pendentes.
     */
    @GetMapping("/sincronizacao/mapeamento-sku")
    public MapeamentoSkuService.Resultado mapeamentoSku(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) CriterioDataEnvio criterio,
            HttpServletRequest request
    ) {
        return mapeamentoSkuService.mapear(pedidosDoDia(data, criterio, request));
    }

    @GetMapping("/sincronizacao/pedidos-do-dia")
    public List<EnvioMercadoLivre> pedidosDoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) CriterioDataEnvio criterio,
            HttpServletRequest request
    ) {
        if (request.getHeader("X-Forwarded-For") != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Endpoint disponivel apenas via localhost.");
        }
        LocalDate dia = data != null ? data : LocalDate.now();
        return criterio != null
                ? orderService.buscarEnviosDoDia(dia, criterio)
                : orderService.buscarEnviosDoDia(dia);
    }
}
