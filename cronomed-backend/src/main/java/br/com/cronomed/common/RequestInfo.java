package br.com.cronomed.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Utilitarios para ler dados da requisicao corrente (IP de origem, tipo de cliente). */
public final class RequestInfo {

    public static final String CLIENT_HEADER = "X-Client-Type";

    private RequestInfo() {
    }

    public static HttpServletRequest current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    public static String ip() {
        HttpServletRequest req = current();
        return req == null ? null : req.getRemoteAddr();
    }

    /** Cliente web envia "X-Client-Type: web"; qualquer outro valor e tratado como mobile. */
    public static Source clientSource(HttpServletRequest req) {
        String h = req.getHeader(CLIENT_HEADER);
        return "web".equalsIgnoreCase(h) ? Source.WEB : Source.MOBILE;
    }

    public static boolean isWeb(HttpServletRequest req) {
        return clientSource(req) == Source.WEB;
    }
}
