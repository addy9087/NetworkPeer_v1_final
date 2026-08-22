-- FCM Retry-After values must outlive a worker process. Pending rows are not
-- eligible until this durable timestamp, while PROCESSING lease recovery keeps
-- its existing crash-recovery behavior.
ALTER TABLE public.sync_events
  ADD COLUMN push_not_before_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_sync_events_push_pending_due
  ON public.sync_events (push_not_before_at ASC, id ASC)
  WHERE push_state = 'PENDING';
