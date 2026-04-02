# ModNVote

**Modern, auditable community voting for PaperMC servers**

ModNVote is an open-source Minecraft plugin designed to provide **transparent, verifiable, and user-friendly voting systems** for modern server communities.

Originally built as a simple Yes/No voting tool, ModNVote is now undergoing a full redesign into a **ballot-based, audit-driven voting platform** capable of supporting ranked polls, elections, and verifiable results.

Developed by [MODN METL LTD](https://modnmetl.com).

![CI](https://github.com/MODNMETL/ModNVote/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)

---

## 🚧 ModNVote 2.0 (In Development)

> ModNVote 2.0 is a **ground-up redesign** of the plugin.  
> Early versions are functional but evolving toward a full-featured voting platform.

### Why 2.0?

Version 1.x provided:
- simple Yes/No voting
- privacy-conscious design
- integrity seal for tamper detection

However, it had limitations:
- single poll model
- no ranked or multi-choice voting
- limited audit depth
- no external verification capability

### 2.0 introduces:

- Multi-poll architecture
- Ballot-first data model
- Ranked voting (IRV-style)
- Future STV election support
- Append-only audit chain
- External witness publication (e.g. Discord)
- Deterministic recounting
- Unified GUI voting experience (Java + Bedrock)

---

## 🔐 Integrity & Trust Model

ModNVote 2.0 is designed to make voting:

- ✔ Transparent
- ✔ Verifiable
- ✔ Tamper-evident

It does **not** claim to make tampering impossible — instead, it makes it **detectable and provable**.

### Key principles

**Anonymous ballots are the source of truth**  
Vote content is stored as anonymous ballots, separated from voter identity.

**Participation is tracked separately**  
The system records that a player has voted without storing how they voted.

This ensures both:
- verifiable inclusion
- strong voter privacy

**Append-only audit chain**  
All lifecycle events are recorded and hash-linked.

**Deterministic recounting**  
Results can always be recomputed from stored ballots.

**External witnesses (planned)**  
State checkpoints can be published externally (e.g. Discord).

---

## 🗳️ Voting Features (2.0 Direction)

### Phase 1
- Yes / No
- Single-choice polls
- Ranked single-winner (IRV)

### Phase 2
- Multi-winner STV
- Combined elections (Mayor + Council)

---

## 🧠 Voting UX Philosophy

Voting is a **trust interface**, not just a mechanic.

- Clear selection
- Explicit confirmation
- Ranked interaction (ordered preference)
- Two-step commit flow
- Cross-platform consistency (Java + Bedrock)

---

## 🧱 Current 2.0 Capabilities

- Poll creation and storage
- Poll lifecycle (DRAFT → OPEN → CLOSED)
- Ranked poll foundation
- Audit event chain (hash-linked)

### Commands:
```
/modnvote status  
/modnvote reload  
/modnvote list  
/modnvote seedbreed  
/modnvote open <pollId>  
/modnvote close <pollId>
```

---

## 🚀 Installation (Current)

> ⚠️ ModNVote 2.0 requires a **clean install**

1. Remove any existing ModNVote 1.x installation
2. Delete old database/config files
3. Install the 2.0 jar into `/plugins/`
4. Start server

### Requirements
- Paper 1.21.x+
- Java 21

---

## 🧭 Roadmap

### 2.0.0
- Demo Ranked horse-breed poll
- Ballot submission system
- Audit chain foundation
- GUI voting system

### 2.1.x
- Elections (Mayor + Council)
- STV multi-winner voting
- Advanced reporting

---

## ⚠️ Important Notes

- 2.0 is a **clean architectural reset**
- No migration from 1.x is currently supported
- Schema and APIs are still evolving
- Early builds are intended for testing and iteration

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch
3. Build with `./gradlew clean build`
4. Submit a PR with clear rationale

---

## 🔐 Security

If you find a vulnerability:

- Do NOT disclose publicly
- Contact: security@modnmetl.com

---

## 📜 License

MIT License

---

## 🏗️ Credits

- Development Lead: Jamie E. Thompson
- Maintainer: MODN METL LTD
- Community testing: Pinecraft Equestrian SMP

---

> “Trust, but verify.”  
> ModNVote is built to help communities make fair, transparent decisions.
