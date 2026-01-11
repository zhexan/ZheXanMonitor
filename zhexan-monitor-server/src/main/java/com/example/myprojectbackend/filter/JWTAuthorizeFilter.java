package com.example.myprojectbackend.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.myprojectbackend.entity.RestBean;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.service.ClientService;
import com.example.myprojectbackend.utils.Const;
import com.example.myprojectbackend.utils.JWTUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
public class JWTAuthorizeFilter extends OncePerRequestFilter {
    @Resource
    JWTUtils utils;
    @Resource
    ClientService service;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String uri = request.getRequestURI();
        if (uri.startsWith("/monitor")) {
            if(!uri.endsWith("/register")) {
                Client client = service.getClientByToken(authorization);
                if (client == null) {
                    response.setStatus(401);
                    response.getWriter().write(RestBean.failure(401, "未注册").asJSONString());
                    return;
                } else {
                    request.setAttribute(Const.ATTR_CLIENT, client);
                }
            }
        }
        DecodedJWT jwt = utils.resolveJWT(authorization);
        if(jwt != null) {
            UserDetails user = utils.toUser(jwt);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            request.setAttribute("id", utils.toId(jwt));
        }
        filterChain.doFilter(request, response);
    }
}
