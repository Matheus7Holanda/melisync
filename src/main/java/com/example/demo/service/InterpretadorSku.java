package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Le o campo SKU do anuncio, que pode trazer mais de um codigo (kit).
 *
 * Exemplos:
 *   "FJ2090K"                  -> FJ2090K x1
 *   "FJ2870C & FJ2670C"        -> FJ2870C x1, FJ2670C x1
 *   "2 FJ875PC e FJ1150PC"     -> FJ875PC x2, FJ1150PC x1
 *
 * Separadores aceitos entre codigos: "&", "+", ",", ";" e a palavra "e".
 * Numero na frente do codigo (separado por espaco ou "x") e a quantidade
 * daquela peca em cada kit; sem numero, a quantidade e 1.
 */
public final class InterpretadorSku {

    private static final Pattern SEPARADOR = Pattern.compile("\\s*[&+,;]\\s*|\\s+[eE]\\s+");
    private static final Pattern COM_QUANTIDADE = Pattern.compile("^(\\d{1,2})(?:\\s*[xX]\\s*|\\s+)(\\S+)$");
    private static final Pattern CODIGO = Pattern.compile("^\\S+$");

    private InterpretadorSku() {
    }

    public record ParteSku(String codigo, int quantidade) {
    }

    /** @throws IllegalArgumentException se o SKU estiver vazio ou nao der para entender. */
    public static List<ParteSku> interpretar(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU vazio.");
        }
        List<ParteSku> partes = new ArrayList<>();
        for (String bruto : SEPARADOR.split(sku.trim())) {
            String token = bruto.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("SKU com separador sobrando: '" + sku + "'.");
            }
            var m = COM_QUANTIDADE.matcher(token);
            if (m.matches()) {
                int qtd = Integer.parseInt(m.group(1));
                if (qtd < 1) {
                    throw new IllegalArgumentException("Quantidade zero no SKU: '" + sku + "'.");
                }
                partes.add(new ParteSku(m.group(2), qtd));
            } else if (CODIGO.matcher(token).matches()) {
                partes.add(new ParteSku(token, 1));
            } else {
                throw new IllegalArgumentException("Nao consegui ler '" + token + "' no SKU '" + sku + "'.");
            }
        }
        return partes;
    }

    /** SKU comum: um codigo so, uma unidade. */
    public static boolean ehSimples(List<ParteSku> partes) {
        return partes.size() == 1 && partes.get(0).quantidade() == 1;
    }
}
