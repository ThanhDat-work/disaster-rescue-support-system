package vn.edu.hcmute.drss.repositories;

/**
 * Projection chỉ đọc cho kết quả GROUP BY của thống kê yêu cầu cứu hộ.
 */
public interface RequestCountProjection {

    String getCategory();

    long getTotal();
}