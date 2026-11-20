import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";

const isPublicRoute = createRouteMatcher([
  "/",
  "/login(.*)",
  "/mentions-legales",
  "/politique-confidentialite",
  "/plaquette",
  "/api/webhooks(.*)",
  "/monitoring(.*)",
  "/robots.txt",
  "/sitemap.xml",
]);

// /admin/config-ia is accessible to managers; only the back-office routes are SUPER_ADMIN-only
const isAdminRoute = createRouteMatcher(["/admin", "/admin/((?!config-ia).*)"]);

export default clerkMiddleware(async (auth, req) => {
  if (isPublicRoute(req)) return;

  const { userId, sessionClaims, redirectToSignIn } = await auth();
  if (!userId) return redirectToSignIn({ returnBackUrl: req.url });

  const publicMetadata = (sessionClaims?.public_metadata ?? sessionClaims?.publicMetadata) as { isSuperAdmin?: boolean } | undefined;
  const isSuperAdmin = Boolean(publicMetadata?.isSuperAdmin);

  if (isAdminRoute(req) && !isSuperAdmin) {
    return Response.redirect(new URL("/dashboard", req.url));
  }
});

export const config = {
  matcher: [
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    "/(api|trpc)(.*)",
  ],
};
