CREATE TABLE IF NOT EXISTS su_kien (
  id INTEGER PRIMARY KEY,
  luc TEXT NOT NULL,          -- ISO UTC
  loai TEXT NOT NULL,         -- tai | mo | cap-nhat | don | home | khoi-phuc | may-chieu
  may TEXT NOT NULL DEFAULT '',  -- mã máy ngẫu nhiên của app (tải: băm IP+ngày, không lưu IP)
  ban TEXT NOT NULL DEFAULT '',
  tivi TEXT NOT NULL DEFAULT '',
  so INTEGER NOT NULL DEFAULT 0,
  them TEXT NOT NULL DEFAULT '',
  quoc_gia TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS su_kien_loai_luc ON su_kien (loai, luc);
CREATE INDEX IF NOT EXISTS su_kien_may ON su_kien (may);
