import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/dashboard", "/leads", "/inbox", "/import", "/equipe", "/admin", "/planning", "/stats", "/api/"],
      },
    ],
    sitemap: "https://leadrive.fr/sitemap.xml",
  };
}
