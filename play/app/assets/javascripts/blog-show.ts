(() => {
  type Preview = {
    url: string;
    title: string;
    description?: string;
    imageUrl?: string;
    siteName?: string;
    fallback?: boolean;
  };

  const postBody = document.querySelector<HTMLElement>(".post-body");
  const ogpEndpoint = document.body?.dataset?.ogpEndpoint;

  if (!postBody || !ogpEndpoint) {
    return;
  }

  const standaloneParagraphs = Array.from(postBody.querySelectorAll("p")).filter(
    paragraph => {
      const anchors = paragraph.querySelectorAll<HTMLAnchorElement>("a[href]");
      if (anchors.length !== 1 || paragraph.childElementCount !== 1) {
        return false;
      }
      const anchor = anchors[0];
      const text = paragraph.textContent?.trim() ?? "";
      const linkText = anchor.textContent?.trim() ?? "";
      const href = anchor.href.trim();
      return (
        text === linkText &&
        (href.startsWith("http://") || href.startsWith("https://"))
      );
    }
  );

  const linkTextByHref = new Map<string, string>();
  standaloneParagraphs.forEach(paragraph => {
    const anchor = paragraph.querySelector<HTMLAnchorElement>("a[href]");
    if (anchor && !linkTextByHref.has(anchor.href)) {
      linkTextByHref.set(anchor.href, anchor.textContent?.trim() ?? "");
    }
  });

  const links = Array.from(linkTextByHref.keys());
  if (links.length === 0) {
    return;
  }

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
    const previews = await Promise.all(links.map(async url => [url, await fetchPreview(url)] as const));
    const previewByUrl = new Map<string, Preview>(previews);

    standaloneParagraphs.forEach(paragraph => {
      const anchor = paragraph.querySelector<HTMLAnchorElement>("a[href]");
      const preview = anchor ? previewByUrl.get(anchor.href) : undefined;
      if (preview?.title) {
        paragraph.replaceWith(createCard(preview));
      }
    });
  };

  void render();
})();
