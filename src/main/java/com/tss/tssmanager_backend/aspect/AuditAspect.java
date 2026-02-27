package com.tss.tssmanager_backend.aspect;

import com.tss.tssmanager_backend.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(20)
public class AuditAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.tss.tssmanager_backend.service..*(..)) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object setAuditUser(ProceedingJoinPoint joinPoint) throws Throwable {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
                    CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
                    Integer userId = userDetails.getId();

                    entityManager
                            .createNativeQuery("SELECT set_config('app.user_id', '" + userId + "', false)")
                            .getSingleResult();

                }
            } catch (Exception e) {
                System.err.println("AUDIT WARN: No se pudo inyectar usuario: " + e.getMessage());
            }
        }

        return joinPoint.proceed();
    }
}