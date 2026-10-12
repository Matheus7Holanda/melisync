# Integração Mercado Livre → VHSYS

API em Java/Spring Boot que consolida os envios do dia no Mercado Livre em
um único PDV no VHSYS: cada item vendido é rastreado pelo SKU até o produto
correspondente no VHSYS (mesma quantidade e valor recebido), e envios com
logística **FLEX** recebem um desconto de **R$ 13,00 por venda** (não por
item) no valor total daquele envio.

## Status atual

- ✅ Autenticação OAuth2 completa com o Mercado Livre (troca de `code` por
  token, persistência em banco H2, renovação automática via `refresh_token`).
- ✅ `VhsysClient` implementado contra a documentação oficial da API VHSYS:
  busca de produto por SKU (`GET /produtos`) e criação de venda balcão em
  duas chamadas (`POST /vendas-balcao` para o cabeçalho — onde entra o
  desconto FLEX consolidado do dia — e `POST /vendas-balcao/{id_frente}/produtos`
  para os itens).
- ✅ Busca dos **envios de um dia** (`MercadoLivreOrderService`), com dois
  critérios de "dia" (variável `ML_CRITERIO_DATA`, padrão `CRIACAO_PEDIDO`):
  - `CRIACAO_PEDIDO`: pedidos pagos **criados** no dia. Mais simples e
    barato; bom para validar a integração.
  - `SAIDA_ENVIO`: envios que **saíram** no dia (alvo final). Como a API não
    permite buscar envios por data de saída, busca pedidos dos últimos
    `ML_DIAS_RETROATIVOS` dias (padrão 10), lê `/shipments/{id}/history` e
    fica com os que tiveram o status `shipped` ("A caminho") no dia, no
    horário do Brasil. A regra fica isolada em `RegraSaidaEnvio`.

  Nos dois casos os pedidos pagos são agrupados por envio (um carrinho =
  vários pedidos no mesmo envio), identifica envios FLEX (`self_service`) e
  monta o modelo de domínio (`EnvioMercadoLivre` → `ItemVenda`), com o
  desconto de R$ 13,00 aplicado **uma vez por envio** (testado em
  `EnvioMercadoLivreTest`).

  O valor que vai para o VHSYS é o que o vendedor **recebe**:
  `valorRecebidoMl = (valor unitário × quantidade) − tarifa de venda − frete
  pago pelo vendedor` (fórmula da documentação do ML; `CalculoRecebimento`),
  e `valorLiquido = valorRecebidoMl − desconto FLEX`. A tarifa vem de
  `marketplace_fee` dos pagamentos aprovados do pedido e o frete de
  `GET /shipments/{id}/costs` (custo do vendedor). Se o ML não informar o
  frete de um envio, ele é considerado 0 e o envio traz um `avisos` no JSON.
- ⏳ Mapeamento de SKU → produto VHSYS, consolidação da venda do dia, envio
  ao VHSYS e agendamento — próximos passos.

Para conferir o Passo 4 manualmente (acesse sempre por `localhost`; o
endpoint recusa requisições vindas do túnel ngrok):

```
GET http://localhost:8080/sincronizacao/pedidos-do-dia?data=2026-10-10
GET http://localhost:8080/sincronizacao/pedidos-do-dia?data=2026-10-10&criterio=SAIDA_ENVIO
```

## Stack

- Java 21, Spring Boot 4.1.0
- Spring Web MVC + WebClient (chamadas HTTP para ML e VHSYS)
- Spring Data JPA + H2 (arquivo local, para desenvolvimento)
- Maven

## Estrutura

```
com.example.demo
├── DemoApplication.java   # única classe @SpringBootApplication
├── config/                # MercadoLivreProperties, VhsysProperties, WebClientConfig
├── client/                # VhsysClient (produto por SKU, venda balcão)
├── controller/            # MercadoLivreAuthController (fluxo OAuth2)
├── domain/                # MlOAuthToken (entidade JPA)
├── dto/ml/                # DTOs da API do Mercado Livre
├── dto/vhsys/             # DTOs da API do VHSYS
├── repository/            # MlOAuthTokenRepository
├── service/               # MercadoLivreAuthService
├── scheduler/             # (a implementar) job diário de sincronização
└── exception/             # (a implementar) tratamento de erros de integração
```

## Como rodar localmente

### 1. Pré-requisitos

- JDK 21
- Uma aplicação cadastrada em [developers.mercadolivre.com.br](https://developers.mercadolivre.com.br)
  com os scopes `read` e `offline_access` marcados
- [ngrok](https://ngrok.com) (o Mercado Livre exige HTTPS no redirect URI e
  não aceita `localhost` — o ngrok expõe sua porta local com um domínio
  HTTPS público)

### 2. Configure as variáveis de ambiente

Copie `.env.example` como referência dos nomes esperados e configure os
valores reais como variáveis de ambiente do seu ambiente de execução (no
Eclipse: `Run > Run Configurations > [sua config] > aba Environment`).
**Nunca** preencha valores reais em `application.properties` nem em
`.env.example`.

### 3. Suba o túnel ngrok

```bash
ngrok http 8080 --url https://SEU-DOMINIO.ngrok-free.dev
```

Cadastre `https://SEU-DOMINIO.ngrok-free.dev/auth/mercadolivre/callback`
como URI de redirect no app do Mercado Livre, e use essa mesma URL na
variável `ML_REDIRECT_URI`.

### 4. Rode a aplicação

```bash
./mvnw spring-boot:run
```

ou, no Eclipse, rode `DemoApplication` como Java Application.

### 5. Autorize o app no Mercado Livre (uma vez por ambiente)

1. Acesse `GET https://SEU-DOMINIO.ngrok-free.dev/auth/mercadolivre/iniciar`
2. Abra a URL retornada no navegador e autorize
3. O Mercado Livre redireciona de volta para `/auth/mercadolivre/callback`,
   que salva o token no banco — a partir daí o `MercadoLivreAuthService`
   renova sozinho via `refresh_token`.

### Descobrindo o `ML_SELLER_ID`

Depois de autorizar, pegue o `access_token` salvo (via H2 Console, veja
abaixo) e acesse no navegador:

```
https://api.mercadolibre.com/users/me?access_token=SEU_ACCESS_TOKEN
```

O campo `"id"` no topo da resposta é o seu `ML_SELLER_ID`.

### H2 Console (inspecionar o banco local)

Com a aplicação rodando, acesse `http://localhost:8080/h2-console`.

- JDBC URL: `jdbc:h2:file:./data/ml-vhsys-db`
- User Name: `sa`
- Password: *(vazio)*

> **Atenção**: desconecte a sessão do H2 Console antes de reiniciar a
> aplicação — o H2 trava o arquivo do banco com lock exclusivo, e deixar o
> console conectado impede a aplicação de abrir uma nova conexão.

## Notas técnicas

- **Não use `ngrok start`** — sem um túnel configurado previamente ele
  falha. Use sempre `ngrok http 8080 --url ...`.
- **`spring-boot-h2console`** é uma dependência separada, obrigatória a
  partir do Spring Boot 4.x para o H2 Console funcionar (antes vinha junto
  só com o driver H2). Sem ela, `/h2-console` retorna 404.
- Se a aplicação falhar ao subir com `Unable to determine Dialect without
  JDBC metadata`, geralmente é porque outra instância (ou uma sessão do H2
  Console) ainda está com o arquivo do banco travado. Confirme com
  `netstat -ano | findstr 8080` (Windows) que a porta está livre antes de
  rodar de novo.
- A API do VHSYS autentica via **headers** (`access-token`,
  `secret-access-token`, `User-Agent`), não via query param. Não existe
  conceito de "PDV" a escolher — a venda balcão é vinculada direto à
  empresa da conta, criada em duas chamadas: `POST /vendas-balcao`
  (cabeçalho) e `POST /vendas-balcao/{id_frente}/produtos` (itens).

## Licença

Projeto de uso interno/privado — sem licença de distribuição definida.
