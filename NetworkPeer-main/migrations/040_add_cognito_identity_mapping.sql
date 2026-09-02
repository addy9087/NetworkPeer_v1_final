-- Cognito owns authentication identities. Marketplace records retain their
-- internal UUIDs and are linked to Cognito's opaque, immutable `sub` claim.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS cognito_sub VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_cognito_sub
  ON public.users (cognito_sub)
  WHERE cognito_sub IS NOT NULL;

CREATE OR REPLACE FUNCTION public.resolve_cognito_user(
  p_cognito_sub TEXT,
  p_phone_number VARCHAR,
  p_role public.user_role
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id UUID;
  v_existing_role public.user_role;
  v_existing_cognito_sub TEXT;
BEGIN
  IF p_cognito_sub IS NULL
    OR p_cognito_sub <> btrim(p_cognito_sub)
    OR length(p_cognito_sub) = 0
    OR length(p_cognito_sub) > 255
    OR p_phone_number IS NULL
    OR p_phone_number !~ '^\+[1-9][0-9]{1,14}$'
    OR p_role IS NULL
    OR p_role NOT IN ('CLIENT', 'WORKER', 'ADMIN') THEN
    RAISE EXCEPTION 'Invalid Cognito identity mapping request' USING ERRCODE = '22023';
  END IF;

  -- A phone number can never be rebound to a different Cognito subject.
  -- Retrying after a unique conflict makes concurrent first logins safe.
  LOOP
    SELECT u.id, u.role
    INTO v_user_id, v_existing_role
    FROM public.users AS u
    WHERE u.cognito_sub = p_cognito_sub
    FOR UPDATE;

    IF FOUND THEN
      IF v_existing_role <> p_role THEN
        RAISE EXCEPTION 'Cognito role does not match the mapped account' USING ERRCODE = '42501';
      END IF;
      RETURN v_user_id;
    END IF;

    SELECT u.id, u.role, u.cognito_sub
    INTO v_user_id, v_existing_role, v_existing_cognito_sub
    FROM public.users AS u
    WHERE u.phone_number = p_phone_number
    FOR UPDATE;

    IF FOUND THEN
      IF v_existing_role <> p_role OR v_existing_cognito_sub IS NOT NULL THEN
        RAISE EXCEPTION 'Phone number is already bound to another account' USING ERRCODE = '42501';
      END IF;
      UPDATE public.users
      SET cognito_sub = p_cognito_sub,
          is_verified = TRUE
      WHERE id = v_user_id;
      RETURN v_user_id;
    END IF;

    BEGIN
      INSERT INTO public.users (cognito_sub, phone_number, full_name, role, is_verified)
      VALUES (p_cognito_sub, p_phone_number, 'Unnamed user', p_role, TRUE)
      RETURNING id INTO v_user_id;

      IF p_role = 'WORKER' THEN
        INSERT INTO public.worker_profiles (user_id, is_available)
        VALUES (v_user_id, FALSE);
      END IF;
      RETURN v_user_id;
    EXCEPTION WHEN unique_violation THEN
      -- A concurrent subject or phone binding won the race. Re-read it above.
    END;
  END LOOP;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.resolve_cognito_user(TEXT, VARCHAR, public.user_role) FROM PUBLIC;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'networkpeer_app') THEN
    EXECUTE 'GRANT EXECUTE ON FUNCTION public.resolve_cognito_user(TEXT, VARCHAR, public.user_role) TO networkpeer_app';
  END IF;
END;
$$;

DROP FUNCTION IF EXISTS public.register_otp_user(VARCHAR, public.user_role, VARCHAR);
