# AI assistant transcripts

The brief asks for visibility into how AI was used: what was asked, what it produced, and how the
output was applied or changed. This folder holds that record.

| Session | Tool | What it covered | Transcript |
| --- | --- | --- | --- |
| 2026-09-25, 14:09–17:30 (Lisbon) | Claude (claude.ai, linked to the project folder) | Maven/Spring Boot setup, CLAUDE.md, test-failure diagnosis, security review, conventions pass, batching, SQL-recording injection tests, `q` cap, git sync | [conversation](https://claude.ai/code/session_012tLiUGDxWXcivKvt9rYXiy) · [original prompts](prompts.md) · [chat history](2026-09-25-claude-chat-history.md) · [annotated log](2026-09-25-claude-session.md) |
<!-- Add a row per additional session (other tools or chats), with its export or share link. -->

How to read them:
- **[prompts.md](prompts.md)** has every prompt I sent, verbatim, starting with the exercise brief and the
  pairing set-up prompt that framed the whole session.
- The **conversation link** is the full, unedited session, including the assistant's replies.
- **[Chat history](2026-09-25-claude-chat-history.md)** is the same conversation in the repo: my messages
  verbatim and the assistant's final reply to each (tool calls left out).
- The **annotated log** is a condensed version. For each prompt, it lists what the assistant produced and what I
  kept, changed or rejected, with the resulting commit where there is one.
- Commits made with the assistant carry a `Co-Authored-By: Claude` trailer (`git log --grep=Claude`).

## How to open the full conversation

Session: <https://claude.ai/code/session_012tLiUGDxWXcivKvt9rYXiy>

This is the conversation's own address. It opens for anyone once sharing is turned on for it: in the
Claude app, open the conversation, click **Share** (top right), and choose to share it with anyone who
has the link. If the share dialog gives a separate public URL, that URL works too and can replace the
one above. The same address is also in the `Claude-Session:` trailer of every commit made with the
assistant (`git log --grep=Claude-Session`).

A share reflects the conversation up to the moment it is created or updated. Share again at the end
so the latest messages are included.
