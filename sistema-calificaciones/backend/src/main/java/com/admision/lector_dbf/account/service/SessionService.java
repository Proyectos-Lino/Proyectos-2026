package com.admision.lector_dbf.account.service;

import com.admision.lector_dbf.account.model.AccountModels;
import com.admision.lector_dbf.account.model.AccountRequests.BootstrapRequest;
import com.admision.lector_dbf.account.model.AccountRequests.LoginRequest;
import com.admision.lector_dbf.account.model.AccountRequests.SessionView;
import com.admision.lector_dbf.account.repository.AccountRepository;
import com.admision.lector_dbf.account.repository.OrganizationProfileRepository;
import org.springframework.transaction.annotation.Transactional;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final AccountService accountsService;
    private final AccountRepository accounts;
    private final OrganizationAccess access;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final OrganizationProfileRepository profiles;

    public SessionService(AccountService accountsService, AccountRepository accounts, OrganizationAccess access,
                          AuthenticationManager authenticationManager, SecurityContextRepository contexts,OrganizationProfileRepository profiles) {
        this.accountsService = accountsService;
        this.accounts = accounts;
        this.access = access;
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.profiles=profiles;
    }

    @Transactional
    public long bootstrap(BootstrapRequest request) {
        long id= accountsService.bootstrap(request.token(), request.organizationCode(), request.organizationName(),
                request.email(), request.name(), request.password());
        profiles.save(id,request.profile());
        return id;
    }

    public SessionView login(LoginRequest request, HttpServletRequest servletRequest,
                             HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales incorrectas");
        }
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, servletRequest, servletResponse);
        return session(authentication);
    }

    public SessionView session(Authentication authentication) {
        AccountModels.User user = access.current(authentication);
        return new SessionView(user.id(), user.email(), user.name(), accounts.organizationsFor(user.id()));
    }

    public void logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }
}
