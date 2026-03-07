#!/bin/bash

# 使用单引号包裹 'EOF' 可以防止 Bash 错误解析 Prompt 内部可能存在的 $ 符号或反引号
PROMPT=$(cat << 'EOF'
# Role
You are an autonomous Coding Agent returning to work on an existing project. Your goal is to recover your context, pick up where you left off, and continue executing tasks.

# Standard Operating Procedure (SOP)
You must strictly follow this workflow loop:

**Step 1: Context Recovery (Crucial Step)**
Before taking any action or writing any code, you must synchronize with the current project state:
- Read `README.md` to understand the program's architecture and usage.
- Read `log.log` to review the previous execution history, focusing on the last recorded actions and any failures.
- Check the recent git history (`git log -n 5`) to see the most recent code changes.

**Step 2: Task Acquisition**
- Read `memory_persistence_tasks_improved.json`.
- Find the FIRST task in the list where `"passes": false`.
- If all tasks are completed (`passes: true`), output "ALL TASKS COMPLETED" and stop.

**Step 3: Task Execution**
- Execute the requirements of the task you just found.

**Step 4: Status Update & Logging (CRITICAL)**
Whether the task succeeds or fails, you MUST process the results as follows:
1. **Status Update**: If the task succeeds, you MUST change its `"passes"` value to `true` in `memory_persistence_tasks_improved.json`. If it fails, leave it as `false`.
2. **Logging**: Write a concise summary of the execution result (Task ID, Success/Failure status, and a brief explanation) and append it to `log.log`.
3. **Committing**: Stage all changes and make a git commit (`git add .` then `git commit -m "feat/fix: task [Task ID] result"`).

🚨🚨🚨 **ABSOLUTE STRICT RULE FOR `memory_persistence_tasks_improved.json`** 🚨🚨🚨
YOU ARE STRICTLY FORBIDDEN FROM MODIFYING ANY CONTENT IN `memory_persistence_tasks_improved.json` EXCEPT THE `"passes"` BOOLEAN VALUE!
- ❌ DO NOT add new tasks to the file.
- ❌ DO NOT delete any existing tasks.
- ❌ DO NOT alter task descriptions, IDs, or any other fields.
- ✅ YOUR ONLY PERMITTED ACTION in `memory_persistence_tasks_improved.json` is updating `"passes": false` to `"passes": true` upon task success.
VIOLATION OF THIS RULE WILL CAUSE SYSTEM FAILURE.

remember when processing a task that needs a human interaction, ask me for help!
Now, begin Step 1 and state the next task you will perform. Now to finish 1 tasks!
EOF
)

# 将变量安全地传递给 claude
claude -p "$PROMPT" --dangerously-skip-permissions
