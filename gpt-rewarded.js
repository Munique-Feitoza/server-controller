(function () {
  const el = document.getElementById("open_rewards");
  if (!el) return;

  // ==========================================
  // CONFIGURAÇÃO DE DEBUG
  // ==========================================
  const MANUAL_DEBUG = true;
  const DEBUG = MANUAL_DEBUG;

  const log = (...args) => {
    if (DEBUG) {
      console.log(
        "%c[GPT-REWARDED]",
        "color: #e11d48; font-weight: bold; background: #fff1f2; padding: 2px 5px; border-radius: 3px;",
        ...args
      );
    }
  };

  const error = (...args) => {
    console.error("[GPT-REWARDED]", ...args);
  };

  const STORAGE_KEY = "rewarded_last_seen_time";

  // Tempo máximo para tentar carregar o anúncio após o clique
  const AD_FAIL_TIMEOUT = 3000;

  // =========================
  // ELEMENTOS DINÂMICOS
  // =========================
  const titleEl = el.querySelector(".rw-title");
  const descEl = el.querySelector(".rw-desc");
  const btnEl = el.querySelector(".rw-btn");

  // =========================
  // CONFIG
  // =========================
  const config = {
    adUnit: el.dataset.adunit,
    link: el.dataset.link || null,
    timer: parseInt(el.dataset.timer || "0", 10) * 1000,
    scrollPercent: parseFloat(el.dataset.scroll || "0"),

    cooldownMs: parseFloat(el.dataset.cooldown || "2") * 60 * 60 * 1000,
    icon: el.dataset.icon || "ðŸŽ",
    cooldownEnabled: true,

    title: titleEl ? titleEl.innerHTML : el.dataset.title ?? "Desbloquear conteúdo",
    description: descEl ? descEl.innerHTML : el.dataset.description ?? "",
    button: btnEl ? btnEl.innerHTML : el.dataset.button ?? "Continuar",

    buttonColors: [
      "#e11d48",
      "#2563eb",
      "#16a34a",
      "#ea580c",
      "#7c3aed"
    ]
  };

  log("Iniciando script...");
  log("Config:", config);

  // =========================
  // VALIDAÇÃO
  // =========================
  if (!config.adUnit) {
    error("data-adunit nÃ£o definido.");
    return;
  }

  // =========================
  // BLOQUEIO COOLDOWN
  // =========================
  const lastSeenTime = localStorage.getItem(STORAGE_KEY);
  const now = Date.now();

  if (config.cooldownEnabled && lastSeenTime) {
    const timePassed = now - parseInt(lastSeenTime, 10);

    if (timePassed < config.cooldownMs) {
      log("Cooldown ativo. Abortando execuÃ§Ã£o.");
      return;
    }
  }

  // =========================
  // CONTROLE GLOBAL
  // =========================
  let modalTriggered = false;

  function safeCreateModal(source) {
    if (modalTriggered) {
      log("Já disparado anteriormente:", source);
      return;
    }

    modalTriggered = true;
    log("Trigger aceito:", source);

    createModal();
  }

  // =========================
  // AUX UI
  // =========================
  function hideModal() {
    const modal = document.getElementById("reward-overlay");

    if (modal) {
      log("Removendo modal.");
      modal.remove();
    }
  }

  // =========================
  // GPT
  // =========================
  window.googletag = window.googletag || { cmd: [] };

  const state = {
    slot: null,
    granted: false,
    readyEvent: null,
    showRequested: false,
    clickedLink: null,
    failTimer: null,
    rewardedVisible: false,
    closedWithoutAd: false,
  };

  function clearFailTimer() {
    if (state.failTimer) {
      clearTimeout(state.failTimer);
      state.failTimer = null;
    }
  }

  function destroyRewardedSlot() {
    try {
      if (state.slot && window.googletag && googletag.destroySlots) {
        googletag.destroySlots([state.slot]);
        log("Slot destruÃ­do.");
      }
    } catch (e) {
      error("Erro ao destruir slot:", e);
    }

    state.slot = null;
  }

  function closeWithoutAd(reason = "Anúncio não apareceu.") {
    log(reason);

    state.closedWithoutAd = true;

    clearFailTimer();

    const btn = document.getElementById("reward-btn");

    if (btn) {
      btn.disabled = false;
      btn.innerText = config.button;
    }

    hideModal();
    destroyRewardedSlot();
  }

  // function loadGPT() {
  //   if (document.getElementById("gpt-js")) return;

  //   log("Carregando GPT...");

  //   const s = document.createElement("script");
  //   s.src = "https://securepubads.g.doubleclick.net/tag/js/gpt.js";
  //   s.async = true;
  //   s.id = "gpt-js";

  //   s.onerror = () => {
  //     error("Erro ao carregar GPT.");
  //   };

  //   document.head.appendChild(s);
  // }

  function prepareRewarded() {
    googletag.cmd.push(() => {
      log("Preparando rewarded...");

      if (!googletag.defineOutOfPageSlot) {
        error("defineOutOfPageSlot não existe.");
        return;
      }

      if (!googletag.enums || !googletag.enums.OutOfPageFormat) {
        error("googletag.enums não disponÃ­vel.");
        return;
      }

      state.slot = googletag.defineOutOfPageSlot(
        config.adUnit,
        googletag.enums.OutOfPageFormat.REWARDED
      );

      if (!state.slot) {
        error("defineOutOfPageSlot retornou NULL.");
        return;
      }

      state.slot.addService(googletag.pubads());

      googletag.pubads().addEventListener("rewardedSlotReady", (e) => {
        log("Anúncio pronto.");

        state.readyEvent = e;

        if (state.closedWithoutAd) {
          log("Anúncio chegou tarde demais. Ignorando.");
          return;
        }

        if (state.showRequested) {
          clearFailTimer();

          try {
            state.rewardedVisible = true;
            e.makeRewardedVisible();
          } catch (err) {
            error("Erro ao exibir rewarded:", err);
            closeWithoutAd("Falha ao abrir o anúncio.");
          }
        }
      });

      googletag.pubads().addEventListener("rewardedSlotGranted", () => {
        log("Recompensa concedida.");
        state.granted = true;
      });

      googletag.pubads().addEventListener("rewardedSlotClosed", () => {
        clearFailTimer();

        log("Anúncio fechado.");

        if (state.granted) {
          localStorage.setItem(STORAGE_KEY, Date.now().toString());

          const destination = state.clickedLink || config.link;

          log("Destino:", destination);

          if (destination) {
            log("Redirecionando:", destination);
            window.location.href = destination;
          } else {
            log("Sem link, fechando modal.");
            hideModal();
          }
        } else {
          log("Fechado sem recompensa.");
        }

        destroyRewardedSlot();
      });

      googletag.enableServices();

      try {
        googletag.display(state.slot);
      } catch (err) {
        error("Erro ao chamar display:", err);
      }
    });
  }

  function showRewarded() {
    log("Solicitando exibição...");

    const btn = document.getElementById("reward-btn");

    state.showRequested = true;
    state.closedWithoutAd = false;

    if (btn) {
      btn.disabled = true;
      btn.innerText = "Carregando anúncio...";
    }

    clearFailTimer();

    state.failTimer = setTimeout(() => {
      if (!state.readyEvent && !state.rewardedVisible) {
        closeWithoutAd("Timeout: anúncio não carregou em 5 segundos.");
      }
    }, AD_FAIL_TIMEOUT);

    if (state.readyEvent) {
      clearFailTimer();

      try {
        state.rewardedVisible = true;
        state.readyEvent.makeRewardedVisible();
      } catch (err) {
        error("Erro ao exibir rewarded:", err);
        closeWithoutAd("Falha ao abrir o anúncio.");
      }
    } else {
      log("Rewarded ainda não pronto. Aguardando até 5 segundos...");
    }
  }

  function createModal() {
    log("Criando modal...");

    const randomColor = config.buttonColors[
      Math.floor(Math.random() * config.buttonColors.length)
    ];

    const overlay = document.createElement("div");
    overlay.id = "reward-overlay";
    overlay.style =
      "position:fixed;inset:0;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;z-index:999999;";

    const box = document.createElement("div");
    box.style =
      "background:#fff;border-radius:16px;padding:24px;width:90%;max-width:420px;text-align:center;font-family:system-ui;position:relative;";

    box.innerHTML = `
      <div style="font-size:40px">${config.icon}</div>
      <h2 style="margin:16px 0 8px">${config.title}</h2>
      <p style="color:#555;line-height:1.5">${config.description}</p>
      <button id="reward-btn"
        style="margin-top:20px;padding:14px;width:100%;border-radius:10px;background:${randomColor};color:#fff;border:none;font-size:16px;cursor:pointer;font-weight:bold;">
        ${config.button}
      </button>
    `;

    overlay.appendChild(box);
    document.body.appendChild(overlay);

    const btn = document.getElementById("reward-btn");

    if (btn) {
      btn.onclick = showRewarded;
    }
  }

  // =========================
  // TRIGGERS
  // =========================

  const hasScrollOrTimer = config.scrollPercent > 0 || config.timer > 0;

  // SCROLL
  if (config.scrollPercent > 0) {
    log("Scroll ativo:", config.scrollPercent + "%");

    window.addEventListener("scroll", () => {
      if (modalTriggered) return;

      const scrollTop = window.scrollY;
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;

      if (docHeight <= 0) return;

      const progress = (scrollTop / docHeight) * 100;

      if (progress >= config.scrollPercent) {
        log("Scroll atingido.");
        safeCreateModal("scroll");
      }
    });
  }

  // TIMER
  if (config.timer > 0) {
    log("Timer ativo:", config.timer);

    setTimeout(() => {
      log("Timer disparado.");
      safeCreateModal("timer");
    }, config.timer);
  }

  // CLICK
  if (!hasScrollOrTimer) {
    log("Click fallback ativo.");

    document.addEventListener("click", (event) => {
      if (modalTriggered) return;

      const target = event.target;

      const btnCustomEl = target.closest('[class*="btn-custom"]');
      const btnBaseEl = target.closest(".btn-base");
      const outgoingEl = target.closest(".outgoing-link");

      const matched = btnCustomEl || btnBaseEl || outgoingEl;

      if (matched) {
        event.preventDefault();

        const anchor = target.closest("a");

        if (anchor && anchor.href) {
          state.clickedLink = anchor.href;
        }

        log("Click trigger. Link:", state.clickedLink);
        safeCreateModal("click");
      }
    });
  }

  // INIT
  // loadGPT();
  prepareRewarded();

})();