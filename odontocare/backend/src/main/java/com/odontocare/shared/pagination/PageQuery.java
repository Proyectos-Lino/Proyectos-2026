package com.odontocare.shared.pagination;

import com.odontocare.shared.web.ApiException;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class PageQuery {
  @Min(0)
  private int page = 0;

  @Min(1)
  @Max(100)
  private int size = 20;

  @Size(max = 160)
  private String search = "";

  @Size(max = 40)
  private String sort = "name";

  @Pattern(regexp = "asc|desc")
  private String direction = "asc";

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public String getSearch() {
    return search.strip();
  }

  public void setSearch(String search) {
    this.search = search == null ? "" : search;
  }

  public String getSort() {
    return sort;
  }

  public void setSort(String sort) {
    this.sort = sort;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public PageRequest pageable(Map<String, String> allowed) {
    String property = allowed.get(sort);
    if (property == null)
      throw ApiException.badRequest("El campo de ordenación no está permitido.");
    var order = Sort.by(Sort.Direction.fromString(direction), property);
    if (!property.equals("id")) order = order.and(Sort.by("id"));
    return PageRequest.of(page, size, order);
  }
}
