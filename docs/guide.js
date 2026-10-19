(() => {
  'use strict';
  const tabs = [...document.querySelectorAll('[data-device-tab]')];
  const panels = [...document.querySelectorAll('[data-device-panel]')];
  const controls = document.querySelector('.platform-controls');
  function selectDevice(device, moveFocus = false) {
    tabs.forEach(tab => {
      const selected = tab.dataset.deviceTab === device;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (selected && moveFocus) tab.focus();
    });
    panels.forEach(panel => { panel.hidden = panel.dataset.devicePanel !== device; });
  }
  function revealDestination(hash) {
    if (['#install', '#apple-devices', '#iloader', '#developer', '#renew'].includes(hash)) selectDevice('ipad');
    if (hash === '#android') selectDevice('android');
  }
  if (controls) {
    controls.hidden = false;
    selectDevice('ipad');
    revealDestination(window.location.hash);
    tabs.forEach((tab, index) => {
      tab.addEventListener('click', () => selectDevice(tab.dataset.deviceTab));
      tab.addEventListener('keydown', event => {
        let next;
        if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
        else if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
        else if (event.key === 'Home') next = 0;
        else if (event.key === 'End') next = tabs.length - 1;
        else return;
        event.preventDefault();
        selectDevice(tabs[next].dataset.deviceTab, true);
      });
    });
    document.querySelectorAll('a[href^="#"]').forEach(link => {
      link.addEventListener('click', () => revealDestination(link.hash));
    });
    window.addEventListener('hashchange', () => revealDestination(window.location.hash));
    if (window.location.hash) {
      const destination = document.getElementById(window.location.hash.slice(1));
      if (destination) requestAnimationFrame(() => destination.scrollIntoView({behavior: 'instant'}));
    }
  }

  const viewer = document.getElementById('viewer');
  const fullImage = viewer.querySelector('img');
  let opener;
  document.querySelectorAll('.enlarge').forEach(button => {
    const img = button.querySelector('img');
    const frame = button.closest('.media-frame');
    const loaded = () => { frame.classList.remove('is-loading', 'has-error'); };
    const failed = () => { frame.classList.remove('is-loading'); frame.classList.add('has-error'); };
    img.addEventListener('load', loaded);
    img.addEventListener('error', failed);
    if (img.complete) { if (img.naturalWidth) loaded(); else failed(); }
    else frame.classList.add('is-loading');
    frame.querySelector('.retry-image').addEventListener('click', () => {
      frame.classList.remove('has-error');
      frame.classList.add('is-loading');
      const source = img.src;
      img.removeAttribute('src');
      img.src = source;
    });
    button.addEventListener('click', () => {
      if (!img.naturalWidth) return;
      opener = button;
      fullImage.src = img.src;
      fullImage.alt = img.alt;
      viewer.showModal();
    });
  });
  document.getElementById('close-viewer').addEventListener('click', () => viewer.close());
  viewer.addEventListener('click', event => { if (event.target === viewer) viewer.close(); });
  viewer.addEventListener('close', () => { if (opener) opener.focus({preventScroll: true}); });

  const art = document.querySelector('.hero-art');
  if ('IntersectionObserver' in window && art) {
    let visible = true;
    const updateMotion = () => art.classList.toggle('motion-off', !visible || document.hidden);
    const observer = new IntersectionObserver(entries => {
      visible = entries[0].isIntersecting;
      updateMotion();
    });
    observer.observe(art);
    document.addEventListener('visibilitychange', updateMotion);
    window.addEventListener('pagehide', () => observer.disconnect(), {once: true});
    window.addEventListener('pageshow', event => { if (event.persisted) observer.observe(art); });
  }
})();
