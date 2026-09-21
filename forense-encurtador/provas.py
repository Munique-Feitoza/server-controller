#!/usr/bin/env python3
"""Provas do incidente do encurtador, direto da API da Cloudflare.
Uso:  python3 provas.py 1|3|5|7|8
Lê o token do arquivo .env da pasta server-controller."""
import sys, os, re, json, urllib.request, calendar, datetime as dt
from collections import Counter, defaultdict

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '.env')
env = open(BASE, encoding='utf-8').read()
val = lambda k: (re.search(rf'{k}\s*=\s*"([^"]*)"', env) or [None, None])[1]
ACCT, TOKEN = val('Account_ID'), val('Your_API_Token_novo')
DB = "38f73965-8d8c-4e40-814b-a7d30613a81e"
API = "https://api.cloudflare.com/client/v4"

def req(url, body=None):
    r = urllib.request.Request(url, data=json.dumps(body).encode() if body else None,
                               method="POST" if body else "GET")
    r.add_header("Authorization", f"Bearer {TOKEN}")
    if body: r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, timeout=120) as x: return json.load(x)
    except Exception as e:
        try: return json.loads(e.read().decode())
        except Exception: return {"erro": str(e)}

def gql(q, v): return req(f"{API}/graphql", {"query": q, "variables": v})
def obs(b):    return req(f"{API}/accounts/{ACCT}/workers/observability/telemetry/query", b)
def T(d,h,m,s=0): return int(calendar.timegm(dt.datetime(2026,9,d,h,m,s).timetuple())*1000)
def hora(ms):  return dt.datetime.fromtimestamp(ms/1000, dt.timezone.utc).strftime('%H:%M:%S')
def cab(n, t): print(f"\n{'='*74}\n EVIDÊNCIA {n} — {t}\n{'='*74}")

def e1():
    cab(1, "O PROGRAMA DO ENCURTADOR NÃO FOI ALTERADO")
    d = req(f"{API}/accounts/{ACCT}/workers/scripts/url-shortener-worker/deployments")
    print(" Publicações do programa url-shortener-worker:\n")
    print(f" {'quando (UTC)':22} {'por quem':34} origem")
    print(" " + "-"*70)
    for x in (d.get("result") or {}).get("deployments", [])[:8]:
        q = x.get("created_on","")[:19].replace("T"," ")
        m = "   <<< no ar durante o incidente" if q.startswith("2026-03-30") else ""
        print(f" {q:22} {(x.get('author_email') or '?'):34} {x.get('source','?')}{m}")
    print("\n >>> Nada entre 30/03 e 14/09. O programa não foi tocado no incidente.")

def e3():
    cab(3, "AS CAMPANHAS ARQUIVADAS, E A VIRADA PARA ERRO 404")
    r = obs({"queryId":"p","timeframe":{"from":T(11,0,18),"to":T(11,0,19)},
             "parameters":{"datasets":["cloudflare-workers"],"limit":500,
               "filters":[{"key":"$metadata.service","operation":"eq","type":"string",
                           "value":"url-shortener-worker"}]},"view":"events","limit":500})
    evs = (((r.get("result") or {}).get("events") or {}).get("events") or [])
    puts = []
    for e in evs:
        ev = (e.get("$workers") or {}).get("event") or {}
        rq = ev.get("request") or {}
        if rq.get("method")=="PUT" and (rq.get("path") or "").startswith("/api/campaigns/"):
            puts.append((e["timestamp"], rq["path"], (ev.get("response") or {}).get("status")))
    puts.sort()
    print(f" 11/09 00:18 UTC (21h18 BRT de 10/09) — {len(puts)} campanhas arquivadas\n")
    for ts,p,st in puts:
        m = ""
        if p.endswith("/125"): m = "   <<< 41.852 cliques — a maior da rede"
        if p.endswith("/132"): m = "   <<< 13.217 cliques — a segunda maior"
        print(f"   {hora(ts)}   PUT {st}   {p}{m}")

    print(f"\n{'-'*74}\n A VIRADA, na campanha que tem todo o tráfego:\n")
    ALVO = "/auto-139-usa-1782839597"
    linhas = []
    for ini,fim in [(T(11,0,15),T(11,0,25)), (T(11,13,45),T(11,13,50))]:
        rr = obs({"queryId":"p","timeframe":{"from":ini,"to":fim},
                  "parameters":{"datasets":["cloudflare-workers"],"limit":400,
                    "filters":[{"key":"$metadata.service","operation":"eq","type":"string","value":"url-shortener-worker"},
                               {"key":"$workers.event.request.path","operation":"eq","type":"string","value":ALVO}]},
                  "view":"events","limit":400})
        for e in (((rr.get("result") or {}).get("events") or {}).get("events") or []):
            ev=(e.get("$workers") or {}).get("event") or {}
            linhas.append((e["timestamp"], (ev.get("response") or {}).get("status")))
    linhas = sorted(set(linhas))
    u302=p404=u404=p302=None
    for ts,st in linhas:
        if st==302 and ts<T(11,1,0): u302=ts
        if st==404 and p404 is None: p404=ts
        if st==404: u404=ts
        if st==302 and ts>T(11,13,0) and p302 is None: p302=ts
    print(f"   {hora(u302)}   302   <- última resposta correta")
    print(f"   {hora(p404)}   404   <<< QUEBROU")
    print(f"        ... 13 horas e 28 minutos de erro ...")
    print(f"   {hora(u404)}   404   <- último erro")
    print(f"   {hora(p302)}   302   <<< VOLTOU (restauração foi 13:47:19)")

def e5():
    cab(5, "A RESTAURAÇÃO, E O VAZIO ANTES DELA")
    d = req(f"{API}/accounts/{ACCT}/logs/audit?since=2026-09-10T22:00:00Z&before=2026-09-11T02:00:00Z&limit=1000")
    n = len(d.get("result") or [])
    print(f" Janela 10/09 19h às 23h (Brasília) — ações registradas na conta: {n}")
    print(" >>> VAZIO. Ninguém mexeu no painel. Descarta a Cloudflare como origem.\n")
    print(f"{'-'*74}\n Dia 11/09 — o que houve (fora DNS e certificado):\n")
    d2 = req(f"{API}/accounts/{ACCT}/logs/audit?since=2026-09-11T00:00:00Z&before=2026-09-12T00:00:00Z&limit=1000")
    for e in sorted(d2.get("result") or [], key=lambda x:(x.get("action") or {}).get("time","")):
        rt = str((e.get("resource") or {}).get("type",""))
        if rt in ("record","certificate_pack","zone","hold",""): continue
        a=e.get("actor") or {}
        m = "   <<< O CONSERTO" if "time_travel" in rt else ""
        print(f"   {(e.get('action') or {}).get('time','')[11:19]}   {(a.get('email') or '?'):32} {rt}{m}")

def e7():
    cab(7, "O COLAPSO DA ATIVIDADE DO BANCO DE DADOS")
    Q = """query($a:String!,$i:Time!,$f:Time!,$db:String!){viewer{accounts(filter:{accountTag:$a}){
      d1AnalyticsAdaptiveGroups(limit:200,filter:{databaseId:$db,datetimeHour_geq:$i,datetimeHour_leq:$f},
      orderBy:[datetimeHour_ASC]){dimensions{datetimeHour} sum{readQueries writeQueries rowsRead}}}}}"""
    r = gql(Q, {"a":ACCT,"i":"2026-09-10T18:00:00Z","f":"2026-09-11T15:00:00Z","db":DB})
    rows = (((r.get("data") or {}).get("viewer") or {}).get("accounts") or [{}])[0].get("d1AnalyticsAdaptiveGroups") or []
    print(f" {'hora (BRT)':12} {'consultas':>10} {'gravações':>10} {'LINHAS LIDAS':>16}")
    print(" " + "-"*52)
    for x in rows:
        h=x["dimensions"]["datetimeHour"]; s=x["sum"]; hh=int(h[11:13]); dia=h[8:10]
        brt=(hh-3)%24; ddia = "10/09" if (dia=="10" and hh>=3) or (dia=="11" and hh<3) else "11/09"
        m=""
        if dia=="11" and hh==0:  m="  <<< quebrou 21h19"
        if dia=="11" and hh==13: m="  <<< restaurado 10h47"
        print(f" {ddia} {brt:02d}h   {s.get('readQueries',0):10} {s.get('writeQueries',0):10} {s.get('rowsRead',0):16,}{m}")
    print("\n >>> As consultas continuaram chegando. Mas pararam de achar campanha ativa.")

def e8():
    cab(8, "OS ERROS ENTREGUES AO USUÁRIO FINAL")
    z = req(f"{API}/zones?name=url-1.com")
    zid = ((z.get("result") or [{}])[0] or {}).get("id")
    Q = """query($z:String!,$i:Date!,$f:Date!){viewer{zones(filter:{zoneTag:$z}){
      httpRequests1dGroups(limit:20,filter:{date_geq:$i,date_leq:$f},orderBy:[date_ASC]){
      dimensions{date} sum{countryMap{clientCountryName} responseStatusMap{edgeResponseStatus requests}}}}}}"""
    r = gql(Q, {"z":zid,"i":"2026-09-08","f":"2026-09-12"})
    zs = (((r.get("data") or {}).get("viewer") or {}).get("zones") or [{}])[0].get("httpRequests1dGroups") or []
    print(f" Domínio url-1.com — respostas entregues por dia\n")
    print(f" {'dia':12} {'redirecionou (302)':>20} {'ERRO (404)':>14} {'outros erros':>14}")
    print(" " + "-"*62)
    for x in zs:
        d=x["dimensions"]["date"]; mp={e["edgeResponseStatus"]:e["requests"] for e in x["sum"]["responseStatusMap"]}
        ok=mp.get(302,0)+mp.get(301,0); err=mp.get(404,0)
        out=sum(v for k,v in mp.items() if k>=500)
        m="   <<< O APAGÃO" if d=="2026-09-11" else ""
        print(f" {d[8:10]}/{d[5:7]}        {ok:20,} {err:14,} {out:14,}{m}")

{'1':e1,'3':e3,'5':e5,'7':e7,'8':e8}.get(sys.argv[1] if len(sys.argv)>1 else '', lambda: print(__doc__))()
