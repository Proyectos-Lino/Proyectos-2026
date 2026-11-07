package com.admision.lector_dbf.shared;

public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw ApiException.badRequest("Paginación inválida");
    }
}
