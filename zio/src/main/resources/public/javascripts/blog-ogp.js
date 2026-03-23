(function () {
  var postBody = document.querySelector(".post-body");
  var ogpEndpoint = document.body && document.body.dataset ? document.body.dataset.ogpEndpoint : null;
  if (!postBody || !ogpEndpoint) {
    return;
  }

  var paragraphs = Array.prototype.slice.call(postBody.querySelectorAll("p"));
  var entries = [];
  paragraphs.forEach(function (paragraph) {
    var anchors = paragraph.querySelectorAll("a[href]");
    if (anchors.length !== 1 || paragraph.childElementCount !== 1) {
      return;
    }
    var anchor = anchors[0];
    var href = (anchor.href || "").trim();
    if (!/^https?:\/\//.test(href)) {
      return;
    }
    var text = (anchor.textContent || "").trim();
    var whole = (paragraph.textContent || "").trim();
    entries.push({
      paragraph: paragraph,
      href: href,
      text: text,
      isPlainUrlLine: whole === text
    });
  });

  if (entries.length === 0) {
    return;
  }

  function isTwitterUrl(url) {
    try {
      var parsed = new URL(url);
      var host = parsed.hostname.toLowerCase();
      var okHost = host === "x.com" || host.endsWith(".x.com") || host === "twitter.com" || host.endsWith(".twitter.com");
      return okHost && /\/status\/\d+/.test(parsed.pathname);
    } catch (_) {
      return false;
    }
  }

  function isYouTubeUrl(url) {
    try {
      var parsed = new URL(url);
      var host = parsed.hostname.toLowerCase();
      var okHost = host === "youtube.com" || host.endsWith(".youtube.com") || host === "youtu.be" || host.endsWith(".youtu.be");
      if (!okHost) {
        return false;
      }
      return parsed.pathname === "/watch" || parsed.pathname.indexOf("/shorts/") === 0 || parsed.pathname.indexOf("/embed/") === 0 || (host.indexOf("youtu.be") >= 0 && parsed.pathname.length > 1);
    } catch (_) {
      return false;
    }
  }

  function toTwitterStatusUrl(url) {
    var parsed = new URL(url);
    parsed.hostname = "twitter.com";
    parsed.searchParams.set("ref_src", "twsrc%5Etfw");
    return parsed.toString();
  }

  function toYouTubeEmbedUrl(url) {
    try {
      var parsed = new URL(url);
      var host = parsed.hostname.toLowerCase();
      var videoId = "";
      if (host.indexOf("youtu.be") >= 0) {
        videoId = parsed.pathname.replace(/^\/+/, "").split("/")[0] || "";
      } else if (parsed.pathname === "/watch") {
        videoId = parsed.searchParams.get("v") || "";
      } else if (parsed.pathname.indexOf("/shorts/") === 0) {
        videoId = parsed.pathname.split("/")[2] || "";
      } else if (parsed.pathname.indexOf("/embed/") === 0) {
        videoId = parsed.pathname.split("/")[2] || "";
      }
      if (!videoId) {
        return null;
      }
      var embedUrl = new URL("https://www.youtube-nocookie.com/embed/" + encodeURIComponent(videoId));
      var start = parsed.searchParams.get("t") || parsed.searchParams.get("start");
      if (start) {
        embedUrl.searchParams.set("start", start.replace(/s$/i, ""));
      }
      return embedUrl.toString();
    } catch (_) {
      return null;
    }
  }

  function createCard(preview) {
    var wrap = document.createElement("div");
    wrap.className = "link-preview-inline";

    var anchor = document.createElement("a");
    anchor.className = "link-preview";
    anchor.href = preview.url;
    anchor.target = "_blank";
    anchor.rel = "noopener noreferrer";

    if (preview.imageUrl) {
      var image = document.createElement("img");
      image.className = "link-preview-image";
      image.src = preview.imageUrl;
      image.alt = preview.title || "";
      image.loading = "lazy";
      if (preview.fallback) {
        image.classList.add("link-preview-favicon");
      }
      anchor.appendChild(image);
    }

    var content = document.createElement("div");
    content.className = "link-preview-content";

    var site = document.createElement("div");
    site.className = "link-preview-site";
    site.textContent = preview.siteName || preview.url;
    content.appendChild(site);

    var title = document.createElement("div");
    title.className = "link-preview-title";
    title.textContent = preview.title || preview.url;
    content.appendChild(title);

    if (preview.description) {
      var description = document.createElement("div");
      description.className = "link-preview-description";
      description.textContent = preview.description;
      content.appendChild(description);
    }

    anchor.appendChild(content);
    wrap.appendChild(anchor);
    return wrap;
  }

  function createTwitterEmbed(url) {
    var wrap = document.createElement("div");
    wrap.className = "link-preview-inline";
    var blockquote = document.createElement("blockquote");
    blockquote.className = "twitter-tweet";
    blockquote.setAttribute("data-dnt", "true");
    var anchor = document.createElement("a");
    anchor.href = toTwitterStatusUrl(url);
    anchor.textContent = url;
    blockquote.appendChild(anchor);
    wrap.appendChild(blockquote);
    return wrap;
  }

  function createYouTubeEmbed(url) {
    var wrap = document.createElement("div");
    wrap.className = "link-preview-inline";
    var frameWrap = document.createElement("div");
    frameWrap.className = "youtube-embed";
    var iframe = document.createElement("iframe");
    iframe.className = "youtube-embed-frame";
    iframe.src = toYouTubeEmbedUrl(url) || url;
    iframe.title = "YouTube video player";
    iframe.loading = "lazy";
    iframe.allowFullscreen = true;
    iframe.referrerPolicy = "strict-origin-when-cross-origin";
    iframe.setAttribute("allow", "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share");
    frameWrap.appendChild(iframe);
    wrap.appendChild(frameWrap);
    return wrap;
  }

  function fallback(url, text) {
    var parsed = new URL(url);
    return {
      url: url,
      title: text || parsed.host,
      description: url,
      imageUrl: new URL("/favicon.ico", parsed.origin).toString(),
      siteName: parsed.host,
      fallback: true
    };
  }

  function fetchPreview(url, text) {
    return fetch(ogpEndpoint + "?url=" + encodeURIComponent(url) + "&text=" + encodeURIComponent(text || ""))
      .then(function (res) {
        if (!res.ok) {
          return fallback(url, text);
        }
        return res.json().catch(function () {
          return fallback(url, text);
        });
      })
      .catch(function () {
        return fallback(url, text);
      });
  }

  Promise.all(entries.map(function (entry) {
    if (isTwitterUrl(entry.href) || isYouTubeUrl(entry.href) || !entry.isPlainUrlLine) {
      return Promise.resolve({ entry: entry, preview: null });
    }
    return fetchPreview(entry.href, entry.text).then(function (preview) {
      return { entry: entry, preview: preview };
    });
  })).then(function (results) {
    results.forEach(function (item) {
      var entry = item.entry;
      if (isTwitterUrl(entry.href)) {
        entry.paragraph.replaceWith(createTwitterEmbed(entry.href));
        return;
      }
      if (isYouTubeUrl(entry.href)) {
        entry.paragraph.replaceWith(createYouTubeEmbed(entry.href));
        return;
      }
      if (entry.isPlainUrlLine && item.preview) {
        entry.paragraph.replaceWith(createCard(item.preview));
      }
    });

    function loadTwitterWidgets(retryCount) {
      var twttr = window.twttr;
      if (twttr && twttr.widgets && twttr.widgets.load) {
        twttr.widgets.load(postBody);
      } else if (retryCount > 0) {
        window.setTimeout(function () {
          loadTwitterWidgets(retryCount - 1);
        }, 250);
      }
    }

    loadTwitterWidgets(20);
  });
})();
