(() => {
  type Preview = {
    url: string;
    title: string;
    description?: string;
    imageUrl?: string;
    siteName?: string;
    fallback?: boolean;
  };

  type RenderTarget =
    | { kind: "twitter"; url: string }
    | { kind: "ogp"; url: string };

  type LinkEntry = {
    paragraph: HTMLParagraphElement;
    href: string;
    text: string;
    isPlainUrlLine: boolean;
  };

  const postBody = document.querySelector<HTMLElement>(".post-body");
  const ogpEndpoint = document.body?.dataset?.ogpEndpoint;

  if (!postBody || !ogpEndpoint) {
    return;
  }

  const linkEntries: LinkEntry[] = Array.from(postBody.querySelectorAll("p")).reduce(
    (acc, paragraph) => {
      const anchors = paragraph.querySelectorAll("a[href]") as NodeListOf<HTMLAnchorElement>;
      if (anchors.length !== 1 || paragraph.childElementCount !== 1) {
        return acc;
      }
      const anchor = anchors[0];
      const text = paragraph.textContent?.trim() ?? "";
      const linkText = anchor.textContent?.trim() ?? "";
      const href = anchor.href.trim();
      const isHttp = href.startsWith("http://") || href.startsWith("https://");
      if (!isHttp) {
        return acc;
      }
      acc.push({
        paragraph,
        href,
        text: linkText,
        isPlainUrlLine: text === linkText
      });
      return acc;
    },
    [] as LinkEntry[]
  );

  const linkTextByHref = new Map<string, string>();
  linkEntries.forEach(entry => {
    if (!linkTextByHref.has(entry.href)) {
      linkTextByHref.set(entry.href, entry.text);
    }
  });

  const links = Array.from(linkTextByHref.keys());
  if (links.length === 0) {
    return;
  }

  const isTwitterUrl = (url: string): boolean => {
    try {
      const parsed = new URL(url);
      const host = parsed.hostname.toLowerCase();
      const isHost =
        host === "x.com" ||
        host.endsWith(".x.com") ||
        host === "twitter.com" ||
        host.endsWith(".twitter.com");
      if (!isHost) {
        return false;
      }
      return /\/status\/\d+/.test(parsed.pathname);
    } catch {
      return false;
    }
  };

  const classify = (url: string): RenderTarget =>
    isTwitterUrl(url) ? { kind: "twitter", url } : { kind: "ogp", url };

  const toTwitterStatusUrl = (rawUrl: string): string => {
    const parsed = new URL(rawUrl);
    const normalized = new URL(parsed.toString());
    normalized.hostname = "twitter.com";
    normalized.searchParams.set("ref_src", "twsrc%5Etfw");
    return normalized.toString();
  };

  const createFallback = (url: string, text: string): Preview => {
    const parsed = new URL(url);
    return {
      url,
      title: text || parsed.host,
      description: url,
      imageUrl: new URL("/favicon.ico", parsed.origin).toString(),
      siteName: parsed.host,
      fallback: true
    };
  };

  const createCard = (preview: Preview): HTMLElement => {
    const wrapper = document.createElement("div");
    wrapper.className = "link-preview-inline";

    const anchor = document.createElement("a");
    anchor.className = "link-preview";
    anchor.href = preview.url;
    anchor.target = "_blank";
    anchor.rel = "noopener noreferrer";

    if (preview.imageUrl) {
      const image = document.createElement("img");
      image.className = "link-preview-image";
      image.src = preview.imageUrl;
      image.alt = preview.title;
      image.loading = "lazy";
      if (preview.fallback) {
        image.classList.add("link-preview-favicon");
      }
      anchor.appendChild(image);
    }

    const content = document.createElement("div");
    content.className = "link-preview-content";

    const site = document.createElement("div");
    site.className = "link-preview-site";
    site.textContent = preview.siteName || preview.url;
    content.appendChild(site);

    const title = document.createElement("div");
    title.className = "link-preview-title";
    title.textContent = preview.title;
    content.appendChild(title);

    if (preview.description) {
      const description = document.createElement("div");
      description.className = "link-preview-description";
      description.textContent = preview.description;
      content.appendChild(description);
    }

    anchor.appendChild(content);
    wrapper.appendChild(anchor);
    return wrapper;
  };

  const createTwitterEmbed = (url: string): HTMLElement => {
    const wrapper = document.createElement("div");
    wrapper.className = "link-preview-inline";

    const blockquote = document.createElement("blockquote");
    blockquote.className = "twitter-tweet";
    blockquote.setAttribute("data-dnt", "true");
    const anchor = document.createElement("a");
    anchor.href = toTwitterStatusUrl(url);
    anchor.textContent = url;
    blockquote.appendChild(anchor);
    wrapper.appendChild(blockquote);
    return wrapper;
  };

  const fetchPreview = async (url: string): Promise<Preview> => {
    const text = linkTextByHref.get(url) ?? "";
    try {
      const response = await fetch(
        `${ogpEndpoint}?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`
      );
      if (!response.ok) {
        return createFallback(url, text);
      }
      const preview = (await response.json()) as Preview | null;
      return preview ?? createFallback(url, text);
    } catch {
      return createFallback(url, text);
    }
  };

  const render = async (): Promise<void> => {
    const targets = links.map(classify);
    const ogpUrls = targets.filter(t => t.kind === "ogp").map(t => t.url);
    const previews = await Promise.all(ogpUrls.map(async url => [url, await fetchPreview(url)] as const));
    const previewByUrl = new Map<string, Preview>(previews);

    linkEntries.forEach(entry => {
      const target = classify(entry.href);
      if (target.kind === "twitter") {
        entry.paragraph.replaceWith(createTwitterEmbed(target.url));
        return;
      }

      if (!entry.isPlainUrlLine) {
        return;
      }

      const preview = previewByUrl.get(entry.href);
      if (preview && preview.title) {
        entry.paragraph.replaceWith(createCard(preview));
      }
    });

    const loadTwitterWidgets = (retryCount: number): void => {
      const twttr = (window as any).twttr;
      if (twttr?.widgets?.load) {
        twttr.widgets.load(postBody);
      } else if (retryCount > 0) {
        window.setTimeout(() => loadTwitterWidgets(retryCount - 1), 250);
      }
    };
    loadTwitterWidgets(20);
  };

  void render();
})();
