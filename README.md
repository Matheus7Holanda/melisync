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
- ⏳ Busca de pedidos/envios do dia no Mercado Livre, mapeamento por SKU e
  consolidação com desconto FLEX — ainda não implementados (é o que liga o
  `MercadoLivreAuthService` ao `VhsysClient`).

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
