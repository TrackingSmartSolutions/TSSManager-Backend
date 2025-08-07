package com.tss.tssmanager_backend.aspect;

import com.tss.tssmanager_backend.service.CalendarioService;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CalendarioInvalidationAspect {

    private static final Logger logger = LoggerFactory.getLogger(CalendarioInvalidationAspect.class);

    @Autowired
    private CalendarioService calendarioService;

    // Intercepta cualquier método que termine en "save", "update", "delete", "crear", "actualizar", "eliminar", "marcar*"
    @AfterReturning(pointcut = "execution(* com.tss.tssmanager_backend.service.*Service.*(..)) && " +
            "(execution(* *save*(..)) || " +
            "execution(* *update*(..)) || " +
            "execution(* *delete*(..)) || " +
            "execution(* *crear*(..)) || " +
            "execution(* *actualizar*(..)) || " +
            "execution(* *eliminar*(..)) || " +
            "execution(* *marcar*(..)) || " +
            "execution(* *regenerar*(..)))")
    public void invalidateCalendarioCache() {
        logger.info("Invalidando caché del calendario por operación de modificación de datos");
        try {
            calendarioService.invalidarCacheCalendario();
        } catch (Exception e) {
            logger.error("Error al invalidar caché del calendario", e);
        }
    }

    // Intercepta específicamente operaciones en entidades del calendario
    @AfterReturning(pointcut = "execution(* com.tss.tssmanager_backend.service.CuentaPorCobrarService.*(..)) || " +
            "execution(* com.tss.tssmanager_backend.service.CuentaPorPagarService.*(..)) || " +
            "execution(* com.tss.tssmanager_backend.service.TratoService.*(..)) || " +
            "execution(* com.tss.tssmanager_backend.service.UsuarioService.*(..))")
    public void invalidateCalendarioForSpecificEntities() {
        logger.info("Invalidando caché del calendario por cambio en entidad específica del calendario");
        try {
            calendarioService.invalidarCacheCalendario();
        } catch (Exception e) {
            logger.error("Error al invalidar caché del calendario para entidades específicas", e);
        }
    }
}