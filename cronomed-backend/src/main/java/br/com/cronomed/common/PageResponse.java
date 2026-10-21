package br.com.cronomed.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Resposta paginada estavel (nao expoe a serializacao interna do Spring Data). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
