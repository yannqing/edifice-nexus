import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// 无需认证即可访问的路径
const PUBLIC_PATHS = ["/login", "/sso/oa"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 公开路径直接放行
  if (PUBLIC_PATHS.some((path) => pathname.startsWith(path))) {
    return NextResponse.next();
  }

  // 检查客户端存储的 token（通过 cookie 传递给 middleware）
  // 由于 localStorage 在 middleware 中不可用，使用 cookie 作为补充判断
  const token = request.cookies.get("access_token")?.value;

  // 如果没有 token，重定向到登录页
  if (!token) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirect", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    // 排除静态资源和 API 路由
    "/((?!_next/static|_next/image|favicon.ico|api).*)",
  ],
};
