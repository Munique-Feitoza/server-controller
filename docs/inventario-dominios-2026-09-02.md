# Inventário de domínios da frota

> Levantado em **2026-09-02** a partir dos `server_name` (nginx-rc) e `vhDomain` (OpenLiteSpeed) de cada vhost dos 6 servidores.
> Inclui domínios *alias*, não apenas o nome do app — por isso `de.eswani.com` aparece corretamente sob o app `fr-eswani`.

## Sumário

| | |
|---|---|
| Hostnames totais | **433** |
| Domínios-raiz | **95** |
| Raízes fatiadas entre servidores | **39** |
| Raízes inteiras num só servidor | **56** |
| Hostnames duplicados em 2+ servidores | **45** |

### Servidores

| Servidor | IP | Acesso | Stack | Hostnames | Raízes |
|---|---|---|---|---|---|
| **WinUp** | `142.44.137.130` | `ssh -p 2222 runcloud@142.44.137.130` | nginx-rc | 132 | 45 |
| **Viral** | `66.70.178.190` | `ssh runcloud@66.70.178.190` | nginx-rc | 99 | 33 |
| **Genix** | `158.69.126.90` | `ssh runcloud@158.69.126.90` | nginx-rc | 101 | 34 |
| **Dashboard** | `144.217.255.109` | `ssh runcloud@144.217.255.109` | nginx-rc | 10 | 6 |
| **Hetzner** | `23.88.113.219` | `ssh root@23.88.113.219` | OpenLiteSpeed | 54 | 25 |
| **Novo** | `135.125.8.235` | `ssh root@135.125.8.235` | OpenLiteSpeed | 37 | 8 |

> **O servidor Novo não tem nenhum domínio-raiz exclusivo** — tudo que está lá também existe em outro servidor. Ele é puramente destino de migração.

---

# Parte 1 — Os 39 domínios fatiados entre servidores

Marcas cujos subdomínios estão espalhados por servidores diferentes. É aqui que mora a desorganização.

## Em 4 servidores

### `zapvagas.com` — 18 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `au`, `ca`, `can`, `cz`, `de`, `der`, `es`, `it`, `lt`, `nz`, `tai`, `uae`, `us` |
| Viral | `us` |
| Genix | `fr`, `fra`, `usa` |
| Hetzner | **raiz** |

### `brurj.com` — 17 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `de`, `en`, `it`, `ita`, `pt` |
| Viral | `fr`, `us`, `usa` |
| Genix | **raiz** |
| Novo | `de`, `en`, `fr`, `it`, `ita`, `pt`, `usa` |

## Em 3 servidores

### `noobnews.com` — 27 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | `es`, `lt` |
| Genix | **raiz**, `be`, `mx` |
| Hetzner | `cz`, `de`, `der`, `des`, `en`, `eng`, `engr`, `enr`, `enrs`, `fr`, `fra`, `frar`, `frr`, `gerr`, `gers`, `it`, `ita`, `jp`, `lt`, `ltu`, `prt`, `tec` |

### `debitoecredito.com` — 22 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | **raiz**, `aus`, `be`, `ca`, `cz`, `de`, `der`, `des`, `en`, `eng`, `ens`, `es`, `fr`, `fra`, `ger`, `it`, `lt`, `nz`, `us` |
| Genix | `fr` |
| Hetzner | **raiz**, `fr` |

### `empregonorio.com` — 21 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `app`, `chret`, `christ`, `crist`, `es`, `it`, `usa` |
| Viral | **raiz**, `de`, `fr`, `pt` |
| Novo | `app`, `chret`, `christ`, `crist`, `de`, `es`, `fr`, `it`, `pt`, `usa` |

### `minutolivre.com` — 13 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | **raiz** |
| Genix | `crist`, `de`, `dir`, `en`, `fr`, `us` |
| Novo | `crist`, `de`, `dir`, `en`, `fr`, `us` |

### `astreduweb.com` — 10 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | **raiz**, `fr`, `fra` |
| Genix | `de`, `en`, `us` |
| Novo | `de`, `en`, `fr`, `fra` |

### `vidanoticias.com` — 10 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `au`, `en`, `fr`, `us` |
| Genix | `us` |
| Novo | `credit`, `eu`, `europe`, `fr` |

### `cnnstars.com` — 9 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | `de`, `en`, `es`, `fr`, `it` |
| Hetzner | **raiz** |
| Novo | `de`, `en`, `it` |

### `goodvips.com` — 8 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `de`, `der`, `fr`, `us` |
| Viral | **raiz** |
| Hetzner | `ger`, `jp`, `usa` |

### `123milhass.com` — 7 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `es`, `fr`, `it`, `us` |
| Viral | `fra` |
| Genix | `es` |

### `calcularferias.com.br` — 7 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `en` |
| Viral | `es`, `fr`, `us`, `usa` |
| Dashboard | **raiz** |

### `criarcurriculodegraca.com.br` — 7 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `en` |
| Viral | `es`, `fr`, `fra`, `us`, `usa` |
| Dashboard | **raiz** |

### `ivagas.com` — 5 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | `credit`, `fr` |
| Hetzner | `fr` |
| Novo | `eu`, `europe` |

### `tutorsnews.com` — 4 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `us`, `usa` |
| Viral | **raiz** |
| Hetzner | `fr` |

## Em 2 servidores

### `multcard.com` — 10 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `bra`, `christ`, `en`, `es`, `fr`, `it`, `usa` |
| Genix | `de`, `fra` |

### `curriculogratis.com` — 8 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `app`, `au`, `ca`, `es`, `fr`, `us` |
| Viral | `app2`, `us` |

### `olfworld.com` — 8 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `de`, `en`, `es`, `fr`, `it`, `tai`, `usa` |
| Hetzner | **raiz** |

### `vastocredit.com` — 7 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `fr`, `us` |
| Genix | **raiz**, `fr`, `us`, `www` |

### `winup.network` — 7 hostnames

| Servidor | Hostnames |
|---|---|
| Genix | **raiz**, `blog` |
| Dashboard | `api`, `app`, `erp`, `sorte`, `teste` |

### `kfpanta.com` — 6 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `apps` |
| Genix | **raiz**, `de`, `en`, `fr`, `us` |

### `visavine.com` — 6 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `fr`, `us`, `usa` |
| Viral | **raiz**, `au`, `en` |

### `visocredit.com` — 6 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz**, `de`, `fr`, `fra`, `usa` |
| Genix | `us` |

### `rendasocial.com` — 5 hostnames

| Servidor | Hostnames |
|---|---|
| Genix | **raiz**, `r1`, `r2` |
| Hetzner | `en`, `us` |

### `brasimoveis.com` — 4 hostnames

| Servidor | Hostnames |
|---|---|
| Genix | `de`, `en`, `fr` |
| Hetzner | **raiz** |

### `loansig.com` — 4 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `de`, `en`, `us` |
| Viral | **raiz** |

### `udenze.com` — 4 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `app`, `us`, `usa` |
| Hetzner | **raiz** |

### `choquei.com` — 3 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `en`, `fr` |
| Genix | **raiz** |

### `icardb.com` — 3 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz** |
| Viral | `de`, `fr` |

### `portalaovivo.com` — 3 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `us` |
| Viral | **raiz**, `fr` |

### `sbtmoney.com` — 3 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | **raiz**, `fr` |
| Novo | `fr` |

### `thonews.com` — 3 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `fr`, `us` |
| Viral | **raiz** |

### `worldfee.com` — 3 hostnames

| Servidor | Hostnames |
|---|---|
| Viral | **raiz** |
| Genix | `fr`, `us` |

### `assoprafitas.com` — 2 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `teste` |
| Hetzner | `blog` |

### `blovibe.com` — 2 hostnames

| Servidor | Hostnames |
|---|---|
| Genix | `en` |
| Hetzner | **raiz** |

### `freeei.com` — 2 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `fr` |
| Hetzner | **raiz** |

### `goodmoney1.com` — 2 hostnames

| Servidor | Hostnames |
|---|---|
| Genix | `fr` |
| Hetzner | **raiz** |

### `marciogarcia.blog` — 2 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | **raiz** |
| Genix | `fr` |

### `newstal.com` — 2 hostnames

| Servidor | Hostnames |
|---|---|
| WinUp | `fr` |
| Hetzner | **raiz** |

---

# Parte 2 — Os 56 domínios inteiros num só servidor

## WinUp — 19 raízes

| Domínio-raiz | Hostnames |
|---|---|
| `brurj.com.br` | 5 — **raiz**, `de`, `en`, `fr`, `us` |
| `bellybat.com` | 4 — **raiz**, `de`, `fr`, `us` |
| `goodtips.com` | 4 — `es`, `fr`, `ro`, `us` |
| `hentips.com` | 4 — **raiz**, `en`, `fr`, `us` |
| `eswani.com` | 3 — `de`, `fr`, `us` |
| `sitecristao.com` | 3 — **raiz**, `en`, `es` |
| `dollfinn.com` | 2 — `us`, `usa` |
| `hotelcupom.com` | 2 — **raiz**, `cms` |
| `bbbnews.com.br` | 1 — **raiz** |
| `hoteleiros.com` | 1 — `fr` |
| `marcioponcio.com` | 1 — **raiz** |
| `meiominuto.net` | 1 — **raiz** |
| `nacaocolorada.com.br` | 1 — `de` |
| `pacoteseofertas.com` | 1 — **raiz** |
| `somniu.net` | 1 — **raiz** |
| `starautoracine.com` | 1 — **raiz** |
| `wegochat.com` | 1 — `app` |
| `wpraiz.com.br` | 1 — `us` |
| `xuxa.blog` | 1 — **raiz** |

## Viral — 11 raízes

| Domínio-raiz | Hostnames |
|---|---|
| `rioimovel.com` | 17 — **raiz**, `cze`, `de`, `des`, `en`, `eng`, `ens`, `es`, `fr`, `fra`, `ger`, `it`, `ita`, `lt`, `ltu`, `prt`, `www` |
| `gifcredit.com` | 4 — **raiz**, `de`, `es`, `fr` |
| `gifmoney.com` | 2 — **raiz**, `fr` |
| `ocurriculocerto.com` | 2 — **raiz**, `fr` |
| `trecobox.com.br` | 2 — `fr`, `us` |
| `cantadasonline.com` | 1 — **raiz** |
| `clubedoesporte.com` | 1 — **raiz** |
| `jornaldosesportes.com` | 1 — `fr` |
| `minutocarioca.com` | 1 — **raiz** |
| `thecodecar.com` | 1 — **raiz** |
| `tododiacomjesus.com` | 1 — `fr` |

## Genix — 14 raízes

| Domínio-raiz | Hostnames |
|---|---|
| `clopnews.com` | 11 — **raiz**, `af`, `aff`, `aus`, `ca`, `caf`, `cz`, `de`, `en`, `es`, `eua` |
| `minutocard.com` | 9 — **raiz**, `br`, `de`, `en`, `eng`, `es`, `fr`, `it`, `us` |
| `btpesportiva.com` | 5 — `de`, `en`, `eng`, `it`, `us` |
| `divaniceni.com` | 5 — **raiz**, `de`, `en`, `es`, `fr` |
| `goodtips.co.za` | 5 — **raiz**, `es`, `fr`, `ro`, `us` |
| `goodud.com` | 5 — **raiz**, `en`, `fr`, `fra`, `frar` |
| `cursosesc.com` | 3 — **raiz**, `fr`, `us` |
| `gansgood.com` | 3 — `en`, `fr`, `fra` |
| `pantauivg.com` | 3 — **raiz**, `fr`, `us` |
| `lunavide.com` | 2 — **raiz**, `fr` |
| `zenlupe.com` | 2 — **raiz**, `fr` |
| `empregafacil.com` | 1 — **raiz** |
| `meiominuto.com` | 1 — **raiz** |
| `whitebeavers.com` | 1 — `us` |

## Dashboard — 3 raízes

| Domínio-raiz | Hostnames |
|---|---|
| `colecionadores.com` | 1 — **raiz** |
| `contabilidadejordao.com.br` | 1 — **raiz** |
| `desgostei.com` | 1 — **raiz** |

## Hetzner — 9 raízes

| Domínio-raiz | Hostnames |
|---|---|
| `goodstale.com` | 2 — **raiz**, `fr` |
| `americandiplomatic.org` | 1 — **raiz** |
| `bulanews.com` | 1 — **raiz** |
| `goodgall.com` | 1 — **raiz** |
| `informerio.com` | 1 — **raiz** |
| `negoceonline.com` | 1 — **raiz** |
| `pancholos.com` | 1 — **raiz** |
| `robodopix.com` | 1 — **raiz** |
| `tirodejaneiro.com` | 1 — **raiz** |

## Novo — 0 raízes

*Nenhuma raiz exclusiva.*

---

# Parte 3 — Os 45 hostnames duplicados

O mesmo hostname existe **fisicamente em 2+ servidores**. Só um atende (o que o DNS aponta); os demais são cópias órfãs — peso morto e superfície de ataque, já que ninguém as mantém atualizadas.

| Servidor | Cópias órfãs |
|---|---|
| WinUp | 16 |
| Viral | 11 |
| Genix | 10 |
| Hetzner | 1 |

| Hostname | Existe em | DNS aponta para |
|---|---|---|
| `app.empregonorio.com` | Novo, WinUp | Novo |
| `brurj.com` | Genix, WinUp | Novo — **quebrado (sem vhost)** |
| `calcularferias.com.br` | Dashboard, WinUp | Dashboard |
| `chret.empregonorio.com` | Novo, WinUp | Novo |
| `christ.empregonorio.com` | Novo, WinUp | Novo |
| `crist.empregonorio.com` | Novo, WinUp | Novo |
| `crist.minutolivre.com` | Genix, Novo | Novo |
| `de.astreduweb.com` | Genix, Novo | Novo |
| `de.brurj.com` | Novo, WinUp | Cloudflare → NOVO |
| `de.cnnstars.com` | Novo, Viral | Cloudflare → NOVO+VIRAL |
| `de.empregonorio.com` | Novo, Viral | Novo |
| `de.minutolivre.com` | Genix, Novo | Novo |
| `debitoecredito.com` | Hetzner, Viral | Hetzner |
| `dir.minutolivre.com` | Genix, Novo | Novo |
| `en.astreduweb.com` | Genix, Novo | Novo |
| `en.brurj.com` | Novo, WinUp | Novo |
| `en.cnnstars.com` | Novo, Viral | Cloudflare → NOVO+VIRAL |
| `en.minutolivre.com` | Genix, Novo | Novo |
| `es.123milhass.com` | Genix, WinUp | Cloudflare (origem indeterminada) |
| `es.empregonorio.com` | Novo, WinUp | Novo |
| `fr.astreduweb.com` | Novo, Viral | Novo |
| `fr.brurj.com` | Novo, Viral | Novo |
| `fr.debitoecredito.com` | Genix, Hetzner, Viral | Hetzner |
| `fr.empregonorio.com` | Novo, Viral | Novo |
| `fr.ivagas.com` | Hetzner, Viral | Viral |
| `fr.minutolivre.com` | Genix, Novo | Novo |
| `fr.sbtmoney.com` | Novo, Viral | Novo |
| `fr.vastocredit.com` | Genix, WinUp | Cloudflare (origem indeterminada) |
| `fr.vidanoticias.com` | Novo, WinUp | Novo |
| `fra.astreduweb.com` | Novo, Viral | Novo |
| `it.brurj.com` | Novo, WinUp | Novo |
| `it.cnnstars.com` | Novo, Viral | Cloudflare → NOVO+VIRAL |
| `it.empregonorio.com` | Novo, WinUp | Novo |
| `ita.brurj.com` | Novo, WinUp | Novo |
| `lt.noobnews.com` | Hetzner, Viral | Hetzner |
| `pt.brurj.com` | Novo, WinUp | Novo |
| `pt.empregonorio.com` | Novo, Viral | Novo |
| `us.curriculogratis.com` | Viral, WinUp | Viral |
| `us.minutolivre.com` | Genix, Novo | Novo |
| `us.vastocredit.com` | Genix, WinUp | Cloudflare (origem indeterminada) |
| `us.vidanoticias.com` | Genix, WinUp | Genix |
| `us.zapvagas.com` | Viral, WinUp | `51.91.54.186` — **servidor externo** |
| `usa.brurj.com` | Novo, Viral | Novo |
| `usa.empregonorio.com` | Novo, WinUp | Novo |
| `vastocredit.com` | Genix, WinUp | Cloudflare (origem indeterminada) |

---

# Parte 4 — Anomalias que precisam de decisão

**1. `brurj.com` está quebrado no HTTPS.** O DNS aponta pro Novo, que tem vhost apenas para `de/en/fr/it/ita/pt/usa.brurj.com` — **falta o domínio raiz**. Resultado: `SSL: no alternative certificate subject name matches`. Só HTTP responde (200, caindo no vhost default). O conteúdo real do `brurj.com` está no WinUp, pra onde o DNS não aponta. *(não alterado — a pedido)*

**2. `us.zapvagas.com` aponta para `51.91.54.186`**, servidor OVH que não é nenhum dos 6 inventariados, e responde 200. Porém o hostname existe no WinUp **e** no Viral. Há infraestrutura fora do inventário atendendo produção.

**3. `de/en/it.cnnstars.com` respondem 200 no Novo *e* no Viral simultaneamente**, com conteúdo quase idêntico. Ambas as cópias estão vivas; quem decide qual atende é a Cloudflare. Editar no servidor errado faz o conteúdo divergir silenciosamente.

**4. `vastocredit.com` está duplicado por completo** (raiz, `fr` e `us`) no WinUp e no Genix, e nenhum dos dois responde por IP direto — só via Cloudflare. A origem precisa ser conferida no painel da Cloudflare.

**5. `calcularferias.com.br` tem o domínio raiz em dois servidores** (WinUp e Dashboard). O DNS aponta pro Dashboard.

**6. A migração para o servidor Novo está pela metade.** Os sites migrados (brurj, empregonorio, minutolivre, astreduweb, cnnstars, sbtmoney) continuam existindo nos servidores de origem, gerando a maior parte das cópias órfãs.

---

## Ressalva de método

Este inventário reflete os **vhosts configurados** — ou seja, o que cada servidor *está preparado para atender*, não necessariamente o que recebe tráfego. Um vhost pode existir com o site morto há meses.

Para decidir o que desligar em vez de migrar, o passo seguinte é cruzar esta lista com os `access_log` de cada app e verificar quais realmente recebem visitas.
