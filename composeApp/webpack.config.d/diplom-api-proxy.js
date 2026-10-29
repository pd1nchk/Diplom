// Прокси API на Ktor (localhost:8080). Клиент ходит на /auth/... того же origin, что и dev-server.
config.devServer = config.devServer || {};

function diplomApiPath(pathname) {
    // webpack иногда передает путь без ведущего "/"
    const p = (pathname || "").startsWith("/") ? pathname : `/${pathname}`;
    return (
        p.startsWith("/auth") ||
        p.startsWith("/profile") ||
        p.startsWith("/schedule") ||
        p.startsWith("/attendance") ||
        p.startsWith("/notifications") ||
        p.startsWith("/state") ||
        p.startsWith("/admin") ||
        p.startsWith("/debug")
    );
}

const diplomProxy = {
    context: diplomApiPath,
    target: "http://127.0.0.1:8080",
    changeOrigin: true,
    secure: false,
};

const prev = config.devServer.proxy;
if (Array.isArray(prev)) {
    config.devServer.proxy = [...prev, diplomProxy];
} else if (prev) {
    config.devServer.proxy = [prev, diplomProxy];
} else {
    config.devServer.proxy = [diplomProxy];
}
