CREATE SCHEMA IF NOT EXISTS moveiq;
CREATE TABLE IF NOT EXISTS moveiq.situation (
  situation_id text PRIMARY KEY,
  situation_type text NOT NULL,
  business_unit text NOT NULL,
  title text NOT NULL,
  description text NOT NULL,
  status text NOT NULL,
  priority text NOT NULL,
  correlation_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS moveiq.evidence_atom (
  evidence_id text PRIMARY KEY,
  situation_id text NOT NULL REFERENCES moveiq.situation(situation_id),
  claim_class text NOT NULL,
  metric_id text,
  payload jsonb NOT NULL,
  available_at timestamptz,
  computed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS moveiq.action_proposal (
  proposal_id text PRIMARY KEY,
  situation_id text NOT NULL REFERENCES moveiq.situation(situation_id),
  action_type text NOT NULL,
  status text NOT NULL,
  evidence_hash text NOT NULL,
  proposed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS moveiq.execution_receipt (
  execution_id text PRIMARY KEY,
  proposal_id text NOT NULL REFERENCES moveiq.action_proposal(proposal_id),
  idempotency_key text NOT NULL UNIQUE,
  adapter text NOT NULL,
  status text NOT NULL,
  external_reference text,
  executed_at timestamptz NOT NULL DEFAULT now()
);
