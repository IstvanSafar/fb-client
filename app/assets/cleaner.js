// Runs after every page load. Before it, the Java side sets window.__fbcSettings
// (AppSettings.java). Facebook rewrites its page often, so this is best effort: if something
// stops being hidden, this is the place to fix it.
// Safety rule: better to leave something visible than to make the page disappear.
// Every hidden element is marked data-fbc-hidden="<filter>"; if the filter is switched off,
// or the element's content is replaced (Facebook recycles elements), it is shown again.
(function () {
  // Labels per filter (Hungarian and English page). Exact match on a leaf element's text.
  var FILTER_LABELS = {
    hideSponsored: ['Sponsored', 'Szponzorált', 'Hirdetés'],
    hideSuggested: ['Javasolt neked', 'Suggested for you', 'Neked javasolt'],
    hideUnfollowed: ['Csatlakozás', 'Join', 'Követés', 'Follow'],
    hideReelsInFeed: ['Reels', 'Reels és rövid videók', 'Reels and short videos'],
    hidePeopleYouMayKnow: ['Ismerősnek jelölhetsz', 'People you may know', 'Ismerősök, akiket ismerhetsz'],
    hideSuggestedGroups: ['Javasolt csoportok', 'Suggested groups', 'Csoportok, amelyekhez csatlakozhatsz']
  };
  var APP_BANNER_TEXTS = [/Open app/i, /Get the app/i, /Install Facebook/i,
                          /Telepítsd a Facebook/i, /Megnyitás az alkalmazásban/i,
                          /Alkalmazás megnyitása/i, /Töltsd le az alkalmazást/i];
  var MARK = 'fbcHidden'; // dataset key -> data-fbc-hidden

  function settings() { return window.__fbcSettings || { hideSponsored: true, hideAppBanners: true }; }

  // Feed filters run only on the feed (on Reels, Groups and Marketplace pages "Join" or
  // "Reels" is part of the content)
  function onFeed() {
    var p = location.pathname;
    return p === '/' || p === '/home.php' || p === '/index.php';
  }

  function leafText(el) {
    return el.children.length <= 1 ? (el.textContent || '').trim() : '';
  }

  function countLabels(el, labels) {
    var n = 0, s = el.querySelectorAll('span, a, div');
    for (var i = 0; i < s.length && n < 2; i++) {
      if (labels.indexOf(leafText(s[i])) !== -1) n++;
    }
    return n;
  }

  // Hideable: not the page skeleton, no input fields, and no bigger than a post
  function isSafeToHide(el) {
    if (!el || el === document.body || el === document.documentElement) return false;
    if (el.querySelector('input, textarea, [contenteditable]')) return false;
    if (el.children.length > 12) return false;
    var h = el.offsetHeight;
    return h > 0 && h < window.innerHeight * 2.5;
  }

  // From a label up to the feed item: the highest ancestor that is still safe to hide and
  // (when labels are given) contains only this one label
  function postContainer(el, labels) {
    var best = null, n = el;
    for (var i = 0; i < 15 && n.parentElement; i++) {
      n = n.parentElement;
      if (!isSafeToHide(n) || (labels && countLabels(n, labels) !== 1)) break;
      best = n;
    }
    return best && best.offsetHeight > 80 ? best : null;
  }

  function bannerContainer(el) {
    var best = null, n = el;
    for (var i = 0; i < 5 && n; i++) {
      if (!isSafeToHide(n) || n.offsetHeight > 250) break;
      best = n;
      n = n.parentElement;
    }
    return best;
  }

  function hide(el, reason) {
    if (el && !el.dataset[MARK]) { el.dataset[MARK] = reason; el.style.display = 'none'; }
  }

  function matchesKeyword(text, words) {
    var t = text.toLowerCase();
    for (var i = 0; i < words.length; i++) if (t.indexOf(words[i]) !== -1) return true;
    return false;
  }

  // Facebook's own AI writes a description into every image's alt text ("May be an image of
  // dog, grass"), so keywords also match what is in the pictures
  function imageDescriptions(el) {
    var imgs = el.querySelectorAll('img[alt]'), out = [];
    for (var i = 0; i < imgs.length; i++) out.push(imgs[i].alt);
    return out.join(' ');
  }

  // Does the reason for hiding still hold (not after a filter is switched off or the content changed)
  function stillMatches(el, s) {
    var reason = el.dataset[MARK];
    if (reason === 'hideAppBanners') return !!s.hideAppBanners;
    if (reason === 'keyword') {
      return (s.keywords || []).length > 0 && onFeed() &&
             matchesKeyword((el.textContent || '') + ' ' + imageDescriptions(el), s.keywords);
    }
    if (!s[reason] || !FILTER_LABELS[reason]) return false;
    if (reason !== 'hideSponsored' && !onFeed()) return false;
    return countLabels(el, FILTER_LABELS[reason]) >= 1;
  }

  function clean() {
    var s = settings();

    var hidden = document.querySelectorAll('[data-fbc-hidden]');
    for (var h = 0; h < hidden.length; h++) {
      if (!stillMatches(hidden[h], s)) {
        hidden[h].style.display = '';
        delete hidden[h].dataset[MARK];
      }
    }

    var active = [];
    for (var name in FILTER_LABELS) {
      if (!s[name]) continue;
      if (name !== 'hideSponsored' && !onFeed()) continue;
      active.push(name);
    }
    var words = onFeed() ? (s.keywords || []) : [];

    var els = document.body.querySelectorAll('span, a, div[role="button"]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.closest('[data-fbc-hidden]')) continue;
      var t = leafText(el);
      if (!t || t.length > 150) continue;

      var found = false;
      for (var a = 0; a < active.length && !found; a++) {
        if (FILTER_LABELS[active[a]].indexOf(t) !== -1) {
          hide(postContainer(el, FILTER_LABELS[active[a]]), active[a]);
          found = true;
        }
      }
      if (found) continue;
      if (s.hideAppBanners && APP_BANNER_TEXTS.some(function (x) { return x.test(t); })) {
        hide(bannerContainer(el), 'hideAppBanners');
      } else if (words.length && matchesKeyword(t, words)) {
        hide(postContainer(el, null), 'keyword');
      }
    }

    if (words.length) {
      var imgs = document.body.querySelectorAll('img[alt]');
      for (var m = 0; m < imgs.length; m++) {
        var img = imgs[m];
        if (img.alt.length < 3 || img.closest('[data-fbc-hidden]')) continue;
        if (matchesKeyword(img.alt, words)) hide(postContainer(img, null), 'keyword');
      }
    }
  }

  // Larger text. Facebook's server lays out the page for 100% text: it breaks the lines
  // itself (white-space: pre), and gives every box a fixed pixel height. With a bigger
  // text size (WebSettings.setTextZoom) the text spills out of its box, over the photo below.
  // For longer texts (post bodies) the line breaks are allowed to wrap again and the fixed
  // heights above the text grow with it. Short labels (names, buttons, "Photo") are left
  // alone: changing their boxes breaks the header and the stories row.
  function fitLargeText() {
    if (!(settings().textZoom > 100)) return;
    var texts = document.querySelectorAll('.native-text');
    for (var i = 0; i < texts.length; i++) {
      var t = texts[i], s = t.textContent;
      if (s.length < 25) continue;
      if (s.length < 60 && s.indexOf('\n') < 0 && t.scrollWidth <= t.clientWidth + 1) continue;
      if (t.dataset.fbcFit === String(s.length)) continue; // done, and not recycled since
      t.dataset.fbcFit = String(s.length);
      t.style.whiteSpace = 'pre-wrap';
      t.style.overflowWrap = 'anywhere';
      var spans = t.querySelectorAll('span');
      for (var j = 0; j < spans.length; j++) spans[j].style.lineHeight = '1.25';
      // Fixed heights from the text up to the first box that already sizes to its content.
      // The old height stays as a minimum: Facebook places some boxes with negative margins
      // (a name next to the profile picture), and with plain 'auto' those boxes would shrink.
      for (var e = t.parentElement; e && e !== document.body; e = e.parentElement) {
        var h = e.style.height;
        if (h === 'auto' || h === 'initial') break;
        if (h) {
          e.style.minHeight = h;
          e.style.height = 'auto';
        }
      }
    }
  }

  // Autoplay. The WebView's own setting (setMediaPlaybackRequiresUserGesture) does not stop
  // Facebook's muted autoplay, so it is controlled here: with autoplayVideos off, play() is
  // allowed only right after a tap (transient user activation), and videos that started on
  // their own are paused.
  function autoplayOff() { return settings().autoplayVideos === false; }
  function userJustTapped() { return !!(navigator.userActivation && navigator.userActivation.isActive); }

  if (!HTMLMediaElement.prototype.__fbcPatched) {
    var originalPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      if (autoplayOff()) {
        if (!userJustTapped()) {
          return Promise.reject(new DOMException('Autoplay is off in fb-client', 'NotAllowedError'));
        }
        this.dataset.fbcUserPlayed = '1';
      }
      return originalPlay.apply(this, arguments);
    };
    HTMLMediaElement.prototype.__fbcPatched = true;
  }

  function stopAutoplayingVideos() {
    if (!autoplayOff()) return;
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      v.autoplay = false;
      if (!v.paused && !v.dataset.fbcUserPlayed) v.pause();
    }
  }

  if (window.__fbcLoaded) { clean(); fitLargeText(); stopAutoplayingVideos(); return; } // new settings: apply right away
  window.__fbcLoaded = true;

  // A video started by the autoplay attribute does not call play(), so catch it here
  document.addEventListener('play', function (e) {
    var v = e.target;
    if (autoplayOff() && !v.dataset.fbcUserPlayed && !userJustTapped()) v.pause();
    else if (userJustTapped()) v.dataset.fbcUserPlayed = '1';
  }, true);

  var pending = false;
  new MutationObserver(function () {
    if (pending) return;
    pending = true;
    setTimeout(function () { pending = false; clean(); fitLargeText(); stopAutoplayingVideos(); }, 400);
  }).observe(document.documentElement, { childList: true, subtree: true });
  clean();
  fitLargeText();
  stopAutoplayingVideos();
})();
