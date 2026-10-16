package com.example.demo.service;

/**
 * Regra da lista "Envios de hoje" do painel do Mercado Livre: o envio esta
 * pronto para despachar (status ready_to_ship) e NAO esta agendado para dias
 * futuros (substatus "buffered", que o painel mostra em "Proximos dias").
 */
public final class RegraEnviosDeHoje {

    static final String PRONTO_PARA_ENVIAR = "ready_to_ship";
    static final String AGENDADO = "buffered";

    private RegraEnviosDeHoje() {
    }

    public static boolean estaNaListaDeHoje(String status, String substatus) {
        return PRONTO_PARA_ENVIAR.equals(status) && !AGENDADO.equals(substatus);
    }
}
