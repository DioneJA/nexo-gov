# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- Residents, applicants, and invited people who submit a public request or follow/interact with a protocol using separate bearer links.
- Public servants who receive, route, review, and conclude protocol work.
- Organization administrators who manage organizational sectors and access.

## Product Purpose

NexoGov is an independent multi-organization SaaS for public administration. Its first implemented end-to-end workflow opens a protocol, produces a receipt, supports token-based public tracking, and lets an authorized servant route, receive, dispatch, and conclude it. Success means completing municipal work with durable, auditable records and reliable organizational isolation.

## Positioning

The product is being built as one configurable platform for independent public organizations, rather than a separate code fork for each municipality. No market leadership, procurement compliance, production-readiness, or service-level claim is established.

## Operating Context

Residents use a public browser interface. Servants process work inside an organization and may route it between sectors. Administrators configure organization structures and access. NexoGov's local development profile contains fictional organizations and accounts; it is not production identity or client data.

## Capabilities and Constraints

- Implemented workflow: public protocol creation, organization-scoped numbering, PDF/QR receipt, individual random-token tracking for the applicant and invited participants, participant-authored public interactions, applicant-controlled participant-token revocation, staff inbox, department forwarding/receipt/dispatch/completion, and organization sector creation with server-side role checks and audit events.
- Participant invitation links prove possession of the token only. They are shown once for manual delivery; they do not verify civil identity and there is no configured e-mail delivery. Participant identity provider credentials and the complete edital POC remain pending.
- The application currently uses local-profile Basic authentication. Production identity, fine-grained roles, secure file storage, integrations, contractual service operations, and most edital requirements remain unfinished.
- Each municipality, autarchy, or public entity must have isolated organization data; an organization can contain multiple sectors. A sector is not itself a tenant.
- The PE 91/2025 from Mata de São João was already awarded and contracted. This product effort therefore does not claim it can win that concluded procurement.

## Brand Commitments

Use the NEXO family blue/navy identity with an Apple HIG-inspired approach to clarity, alignment, restrained color, system typography, and straightforward navigation on every screen. This is the default for new and changed surfaces. The current NexoLar frontend source is a reference for the family palette and shell proportions only. Use NEXO product marks and public-sector information architecture; exclude condominium names, workflows, assets, and data. Do not copy Apple marks, fonts, symbols, or platform-specific chrome.

## Evidence on Hand

- Source implementation in `backend/` and `frontend/`.
- API contract in `contracts/openapi/nexogov-protocol-v1.yaml`.
- Automated PostgreSQL integration and Playwright protocol-flow tests, plus evidence and limits in `docs/INCREMENTO_*.md`.
- Procurement source inventory and requirement matrix in `docs/licitacoes/mata-de-sao-joao-91-2025/`.
- No verified production deployment, contract performance, or 100% procurement conformity evidence.

## Product Principles

- Persist actions and preserve their authorship and history.
- Validate organization membership and permissions on the server for each operation.
- Do not present unimplemented capability as available or verified.
- Keep public access and staff workflows understandable, with clear status and recovery actions.
- Keep product identity and domain rules separate from the reusable NEXO Core.

## Accessibility & Inclusion

The interface must support keyboard operation, visible focus, readable status text, responsive layouts, and reduced motion. Conformance is not yet independently validated.
