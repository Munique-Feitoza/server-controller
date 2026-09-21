# Auditoria domínio → servidor — 2026-09-04

Cruzamento de **402 domínios** cadastrados nos 6 servidores RunCloud contra o destino
real do DNS (API da Cloudflare para 132 zonas + resolver para o resto).

| classificação | qtd | significado |
|---|---|---|
| OK | 341 | domínio aponta para o servidor onde o app está |
| DIVERGE | 46 | aponta para outro IP — o app local não recebe tráfego |
| CF-EDGE | 10 | proxied; origem determinada por comparação de conteúdo |
| SEM-DNS | 5 | domínio não resolve em lugar nenhum |

Fonte dos domínios: `nginx-rc` → `/etc/nginx-rc/conf.d/<app>.domains.d/`,
OpenLiteSpeed → `/etc/lsws-rc/conf.d/<app>.vhosts.d/`.

> **Nenhum app tem mais de um domínio.** Por isso "tirar o domínio errado" deixaria o app
> sem nenhum — o que existe de fato são apps órfãos inteiros.

---

## A. Apagar — cópia duplicada, conteúdo confirmado no destino (34 apps, ~15 GB)

Todos com backup de 2026-09-04 em `/home/backups/wp/`, e o destino confirmado
respondendo 200 pelo domínio.

### Genix (158.69.126.90) — 8 apps, 1.1 GB
`af-clopnews` `aff-clopnews` `aus-clopnews` `ca-clopnews` `caf-clopnews`
`cz-clopnews` `eua-clopnews` → migrados para Contabo `94.72.113.139`
`pantauivg` → `185.158.133.1`

### Hetzner (23.88.113.219) — 9 apps, 8.4 GB
`de-noobnews-hetzner-one` `des-noobnews` `en-noobnews-hetzner-one`
`eng-noobnews-hetzner-one` `enrs-noobnews` `fr-noobnews-hetzner-one`
`fra-noobnews-hetzner-one` → migrados para `137.74.115.183`
`fr-tutorsnews` `zapvagas` → `147.79.91.65`

### Viral (66.70.178.190) — 6 apps, 1.8 GB
`fr-rioimovel` `it-rioimovel` `lt-rioimovel` → `51.91.54.186`
`minutocarioca` → `185.158.133.1`
`fr-ocurriculocerto` `ocurriculocerto` → Contabo `94.72.113.139` (título "FinSavoir")

### WinUp (142.44.137.130) — 11 apps, 3.6 GB
`au-zapvagas` `ca-zapvagas` `it-zapvagas` `nz-zapvagas` → `51.91.54.186`
`blog-xuxa` `hotelcupom` `icardb` `marciogarcia` `marcioponcio` → `185.158.133.1`
`fr-vastocredit` `us-vastocredit` → servidos pelo **Genix** (cópia WinUp é duplicata)

---

## B. NÃO apagar ainda — cópia possivelmente única (9 apps)

O destino não está servindo o site, então apagar aqui pode ser a única cópia viva.

| servidor | app | domínio | situação |
|---|---|---|---|
| WinUp | `es-goodtips` `fr-goodtips` `ro-goodtips` `us-goodtips` | `*.goodtips.com` | domínio não resolve |
| WinUp | `us-wpraiz` | `us.wpraiz.com.br` | domínio não resolve |
| WinUp | `pacoteseofertas` | `pacoteseofertas.com` | parking GoDaddy `13.248.213.45` |
| Genix | `US-btpesportiva` | `us.btpesportiva.com` | destino `147.79.91.65` devolve 403 |
| Genix | `meiominuto` | `meiominuto.com` | destino `147.79.91.65` devolve **500** |
| Hetzner | `goodstale` | `goodstale.com` | destino `147.79.91.65` devolve 403 |

## C. Fora do escopo por ordem

`empregonorio` — 10 apps órfãos (Viral 3, WinUp 7) cujo DNS aponta para o Epyc
`135.125.8.235`. Não tocados.

---

## Duplicata sem desempate

`vastocredit.com` existe idêntico no **WinUp e no Genix** (mesmos 28.945 bytes).
Como está proxied na Cloudflare e não temos permissão de DNS nessa zona, não dá para
saber qual é a origem. Os subdomínios `fr.` e `us.` são servidos pelo Genix.

---

## Correções aplicadas

- **`fr.zenlupe.com`** estava em loop de redirect (301 para si mesmo). Causa: zona em
  SSL **flexible** (Cloudflare falava HTTP com a origem) + o app força HTTPS → loop.
  Zona trocada para **full**. Os 3 hostnames em 200.
- **Modo SSL das 132 zonas auditado**: 116 full, 7 strict, 9 flexible. Trocadas para
  full as 4 zonas flexible com apps nossos, depois de confirmar que a origem serve
  HTTPS em todos os hostnames: `lunavide.com`, `meiominuto.net`, `rioimovel.com`
  (16 hostnames), `tirodejaneiro.com`. Todas verificadas 200 depois.
  Restam em flexible, sem app nosso: `canaldocredito.com`, `empregonorio.com`,
  `leilaoimoveis.com`, `revistacassino.com`, `seguidoress.com`.

## Pendências que dependem de acesso que eu não tenho

- **`us.bellybat.com`** — sem vhost SSL no WinUp; o nginx cai no certificado padrão
  (`123milhass.com`) e o navegador recusa. O app serve 200 em HTTP local. Falta emitir
  Let's Encrypt pelo painel (os outros 3 `bellybat` já têm).
- **`goodmoney1.com`** — registro `AAAA 2a02:4780:13:1183:0:2dbf:6b26:10` (Hostinger
  antiga) convive com o `A 23.88.113.219`. Quem prefere IPv6 cai na máquina morta.
  DNS está em `ns1/ns2.dns-parking.com`, fora da Cloudflare.
- **`desapeguei.com`** — home e `/acervo` em 500 por falta do backend FastAPI.
  As outras 7 rotas em 200.

## Estado geral

Dos 341 domínios que apontam certo, **336 respondem 200**. Os 5 restantes estão
listados acima (`api.winup.network` devolve 404 na raiz, mas é uma FastAPI saudável —
`/health` = 200 — não é falha).
