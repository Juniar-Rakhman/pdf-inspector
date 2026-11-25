package com.interlunium.pdfinspector.resource;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.interlunium.pdfinspector.dto.ErrorDto;
import com.interlunium.pdfinspector.service.NotFoundException;

/**
 * Converts uncaught exceptions into a uniform JSON error payload.
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        int status;
        String message;
        if (exception instanceof WebApplicationException webApplicationException) {
            status = webApplicationException.getResponse().getStatus();
            message = exception.getMessage();
        } else if (exception instanceof NotFoundException) {
            status = 404;
            message = exception.getMessage();
        } else if (exception instanceof IllegalArgumentException illegalArgument) {
            status = 400;
            message = illegalArgument.getMessage();
        } else {
            status = 500;
            message = "Internal server error";
            LOG.error("Unhandled exception", exception);
        }

        ErrorDto error = new ErrorDto(Instant.now(), status,
                Response.Status.fromStatusCode(status).getReasonPhrase(),
                message != null ? message : "No details",
                List.of());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}
