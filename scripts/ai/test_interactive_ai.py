#!/usr/bin/env python3
"""
Interactive AI Chat Client for DANASEA.
Allows interactive conversation with the AI Travel Assistant via Backend API (/api/assistant/chat)
or directly to CLI Proxy in proxy mode (/v1/chat/completions).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parents[2]

# ANSI color codes for formatted terminal output
RESET = "\033[0m"
BOLD = "\033[1m"
GREEN = "\033[32m"
BLUE = "\033[34m"
CYAN = "\033[36m"
YELLOW = "\033[33m"
RED = "\033[31m"
MAGENTA = "\033[35m"
DIM = "\033[2m"


def load_env_file(path: Path) -> dict[str, str]:
    """Parse key-value pairs from .env or .env.example."""
    env: dict[str, str] = {}
    if not path.is_file():
        return env
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip("\"'")
    return env


class DanaseaAiClient:
    def __init__(
        self,
        backend_url: str = "http://localhost:8080",
        proxy_url: str = "http://127.0.0.1:8318/v1",
        proxy_key: str = "",
        access_token: str = "",
        user_email: str = "customer_1@danasea.test",
        mode: str = "backend"
    ):
        self.backend_url = backend_url.rstrip("/")
        self.proxy_url = proxy_url.rstrip("/")
        self.proxy_key = proxy_key
        self.access_token = access_token
        self.user_email = user_email
        self.mode = mode
        self.conversation_id: str | None = None
        self.raw_proxy_history: list[dict[str, str]] = []

    def chat_backend(self, message: str) -> dict[str, Any]:
        """Send message through DANASEA Backend API (/api/assistant/chat)."""
        if not self.access_token:
            return {"error": True, "detail": {"message": "Set DANASEA_ACCESS_TOKEN to a backend access token."}}
        payload: dict[str, Any] = {"message": message}
        if self.conversation_id:
            payload["conversationId"] = self.conversation_id

        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{self.backend_url}/api/assistant/chat",
            data=body,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.access_token}",
                "Accept-Language": "vi"
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=125) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                if "conversationId" in data:
                    self.conversation_id = data["conversationId"]
                return data
        except urllib.error.HTTPError as err:
            err_body = err.read().decode("utf-8")
            try:
                return {"error": True, "code": err.code, "detail": json.loads(err_body)}
            except Exception:
                return {"error": True, "code": err.code, "raw": err_body}
        except Exception as ex:
            return {"error": True, "exception": str(ex)}

    def chat_proxy(self, message: str, model: str = "gemini-3.5-flash-lite") -> dict[str, Any]:
        """Directly call CLI Proxy /v1/chat/completions."""
        self.raw_proxy_history.append({"role": "user", "content": message})
        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": "Bạn là DANASEA AI Assistant, chuyên tư vấn du lịch biển, tour lặn san hô, cano, chèo SUP tại Đà Nẵng. Trả lời thân thiện, hữu ích bằng tiếng Việt."
                },
                *self.raw_proxy_history[-10:]
            ],
            "max_tokens": 2048
        }
        body = json.dumps(payload).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.proxy_key}"
        }
        req = urllib.request.Request(f"{self.proxy_url}/chat/completions", data=body, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                reply = data["choices"][0]["message"]["content"]
                self.raw_proxy_history.append({"role": "assistant", "content": reply})
                return {"status": "success", "message": reply, "model": model}
        except Exception as ex:
            return {"error": True, "exception": str(ex)}

    def reset_conversation(self) -> None:
        """Reset conversation session."""
        self.conversation_id = None
        self.raw_proxy_history.clear()


def print_banner(mode: str, email: str, conv_id: str | None) -> None:
    print(f"\n{BOLD}{CYAN}============================================================{RESET}")
    print(f"{BOLD}{GREEN}           DANASEA AI INTERACTIVE ASSISTANT CLIENT          {RESET}")
    print(f"{BOLD}{CYAN}============================================================{RESET}")
    print(f" • {BOLD}Chế độ hiện tại:{RESET} {YELLOW}{mode.upper()}{RESET} ({'Backend /api/assistant/chat' if mode == 'backend' else 'Direct CLI Proxy'})")
    print(f" • {BOLD}Người dùng giả lập:{RESET} {email}")
    print(f" • {BOLD}Conversation ID:{RESET} {conv_id if conv_id else 'Chưa tạo (Sẽ khởi tạo ở câu hỏi đầu)'}")
    print(f" • {BOLD}Lệnh hỗ trợ:{RESET} {CYAN}/new{RESET} (reset hội thoại), {CYAN}/mode{RESET} (chuyển chế độ), {CYAN}/help{RESET}, {CYAN}/exit{RESET}")
    print(f"{CYAN}------------------------------------------------------------{RESET}\n")


def display_backend_response(resp: dict[str, Any]) -> None:
    if resp.get("error"):
        detail = resp.get("detail")
        err_code = detail.get("code") if isinstance(detail, dict) else None
        err_msg = detail.get("message") if isinstance(detail, dict) else (resp.get("raw") or str(resp.get("exception")))

        if err_code == "AI_MODERATION_BLOCKED":
            friendly_msg = err_msg if (err_msg and err_msg != "Đã xảy ra lỗi không mong muốn.") else "Yêu cầu của bạn đã bị từ chối do vi phạm chính sách an toàn thông tin (phát hiện hành vi can thiệp hệ thống hoặc chèn lệnh injection)."
            print(f"\n{YELLOW}{BOLD}🛡️  CẢNH BÁO AN TOÀN TỪ HỆ THỐNG (AI MODERATION):{RESET}")
            print(f"  {BOLD}⚠️  Thông điệp:{RESET} {YELLOW}{friendly_msg}{RESET}")
            print(f"  {BOLD}🔍 Lý do:{RESET} Yêu cầu bị chặn do hệ thống phát hiện hành vi cố ý can thiệp prompt hệ thống (Jailbreak / Prompt Injection) hoặc cố ý truy vấn/thao tác dữ liệu trái phép.")
            print(f"  {BOLD}💡 Gợi ý:{RESET} Vui lòng nhập câu hỏi phù hợp với dịch vụ du lịch, tour trải nghiệm biển hoặc thời tiết tại Đà Nẵng.\n")
            return

        if err_code == "RATE_LIMIT_EXCEEDED":
            print(f"\n{YELLOW}{BOLD}⏳ CẢNH BÁO GIỚI HẠN TẦN SUẤT (RATE LIMIT):{RESET}")
            print(f"  {BOLD}⚠️  Thông điệp:{RESET} {YELLOW}{err_msg}{RESET}")
            print(f"  {BOLD}💡 Gợi ý:{RESET} Bạn đang gửi yêu cầu quá nhanh. Vui lòng thử lại sau vài giây.\n")
            return

        if err_code == "AI_CONVERSATION_LOCALE_MISMATCH":
            print(f"\n{YELLOW}{BOLD}🌐 CẢNH BÁO NGÔN NGỮ HỘI THOẠI (LOCALE MISMATCH):{RESET}")
            print(f"  {BOLD}⚠️  Thông điệp:{RESET} {YELLOW}{err_msg}{RESET}")
            print(f"  {BOLD}💡 Gợi ý:{RESET} Hội thoại này đang gán với ngôn ngữ khác. Nhập {CYAN}/new{RESET} để bắt đầu cuộc trò chuyện mới.\n")
            return

        if err_code == "AI_SERVICE_UNAVAILABLE":
            print(f"\n{YELLOW}{BOLD}⚠️  DỊCH VỤ AI TẠM THỜI GIÁN ĐOẠN:{RESET}")
            print(f"  {BOLD}Thông điệp:{RESET} {err_msg}")
            print(f"  {BOLD}💡 Gợi ý:{RESET} Kiểm tra trạng thái container CLI Proxy hoặc thử lại sau.\n")
            return

        print(f"\n{RED}{BOLD}❌ LỖI GỌI API ({resp.get('code', 'N/A')}):{RESET}")
        print(f"{RED}{json.dumps(resp.get('detail') or resp.get('raw') or resp.get('exception'), indent=2, ensure_ascii=False)}{RESET}\n")
        return

    message = resp.get("message", "")
    cards = resp.get("cards", [])
    actions = resp.get("actions", [])
    resp_status = resp.get("responseStatus", "SUCCESS")

    print(f"\n{BOLD}{GREEN}🤖 DANASEA AI Assistant:{RESET}")
    print(f"{message}\n")

    if resp_status == "PARTIAL":
        print(f"{YELLOW}⚠️ Lưu ý: Phản hồi được sinh một phần do chạm ngưỡng thời gian xử lý.{RESET}\n")

    # Render Cards (Services / Proposals)
    if cards:
        print(f"{BOLD}{BLUE}📦 THẺ DỊCH VỤ / KẾT QUẢ ĐỀ XUẤT ({len(cards)}):{RESET}")
        for i, card in enumerate(cards, start=1):
            tool = card.get("tool", "")
            card_type = card.get("type", "")
            facts = card.get("facts", {})
            status = facts.get("status", "")
            results = facts.get("results") or facts.get("candidates") or []
            
            print(f"  {CYAN}[{i}] Công cụ:{RESET} {tool} ({card_type}) | {BOLD}Trạng thái:{RESET} {status}")
            
            # Show intent detected by Quyet model if present
            intent = facts.get("intent")
            if intent and isinstance(intent, dict) and intent.get("available"):
                answers = intent.get("answers", {}).get("intent", {})
                detected = answers.get("choice", "N/A")
                conf = answers.get("confidence", 0)
                print(f"      {DIM}🎯 Quyet Small Intent: {detected} (độ tin cậy: {conf:.2%}){RESET}")

            if results:
                for item in results[:5]:  # show top 5 items
                    name = item.get("name") or item.get("serviceName") or item.get("title") or "Dịch vụ"
                    price = item.get("price") or item.get("unitPrice") or "Liên hệ"
                    loc = item.get("location") or item.get("address") or ""
                    print(f"      • {BOLD}{name}{RESET} - Giá: {GREEN}{price} VNĐ{RESET} {f'({loc})' if loc else ''}")
                if len(results) > 5:
                    print(f"      {DIM}... và {len(results) - 5} dịch vụ khác{RESET}")
            elif status == "NO_MATCHES":
                keyword = facts.get("retrievalKeyword") or facts.get("context", {}).get("query")
                print(f"      {DIM}Không tìm thấy dịch vụ phù hợp với từ khóa '{keyword}' trong kho.{RESET}")
        print()

    # Render suggested actions
    if actions:
        print(f"{BOLD}{MAGENTA}💡 HÀNH ĐỘNG ĐỀ XUẤT:{RESET}")
        for act in actions:
            print(f"  ➜ {act.get('label') or act.get('action') or act}")
        print()


def main() -> None:
    parser = argparse.ArgumentParser(description="DANASEA AI Interactive Test Client")
    parser.add_argument("--mode", choices=["backend", "proxy"], default="backend", help="Chế độ chạy (backend hoặc proxy)")
    parser.add_argument("--backend-url", default=None, help="Backend Base URL")
    parser.add_argument("--proxy-url", default=None, help="CLI Proxy Base URL")
    parser.add_argument("--email", default="customer_1@danasea.test", help="Email người dùng test")
    parser.add_argument("--message", default=None, help="Chạy một câu hỏi đơn rồi thoát (không vào REPL)")
    args = parser.parse_args()

    # Load environment variables
    env_file = ROOT_DIR / ".env"
    if not env_file.is_file():
        env_file = ROOT_DIR / ".env.example"
    env = load_env_file(env_file)

    backend_port = env.get("BACKEND_PORT", "8080")
    backend_url = args.backend_url or f"http://127.0.0.1:{backend_port}"
    proxy_url = args.proxy_url or env.get("AI_CLIPROXY_BASE_URL", "http://127.0.0.1:8318/v1")
    # For localhost client, translate internal docker url to host url
    proxy_url = proxy_url.replace("http://cli-proxy-api:8317", "http://127.0.0.1:8318")
    proxy_url = proxy_url.replace("http://host.docker.internal:8318", "http://127.0.0.1:8318")
    proxy_key = env.get("AI_CLIPROXY_API_KEY", "")
    access_token = os.environ.get("DANASEA_ACCESS_TOKEN", "")

    client = DanaseaAiClient(
        backend_url=backend_url,
        proxy_url=proxy_url,
        proxy_key=proxy_key,
        access_token=access_token,
        user_email=args.email,
        mode=args.mode
    )

    if client.mode == "backend" and not client.access_token:
        parser.error("Set DANASEA_ACCESS_TOKEN to a backend access token.")

    # One-shot message mode
    if args.message:
        print(f"Gửi tin nhắn: {args.message}")
        if client.mode == "backend":
            res = client.chat_backend(args.message)
            display_backend_response(res)
        else:
            res = client.chat_proxy(args.message)
            print(f"\n{BOLD}{GREEN}🤖 AI Response:{RESET}\n{res.get('message', res)}")
        if res.get("error"):
            sys.exit(1)
        return

    # Interactive REPL Loop
    print_banner(client.mode, client.user_email, client.conversation_id)

    while True:
        try:
            prompt_label = f"{BOLD}{YELLOW}Bạn [{client.mode.upper()}]{RESET}: "
            user_input = input(prompt_label).strip()
            if not user_input:
                continue

            if user_input.lower() in ("/exit", "/quit", "exit", "quit"):
                print(f"{CYAN}Tạm biệt! Hẹn gặp lại.{RESET}")
                break

            if user_input.lower() in ("/new", "/reset"):
                client.reset_conversation()
                print(f"{GREEN}✓ Đã tạo phiên hội thoại mới. Lịch sử đã được làm mới.{RESET}\n")
                continue

            if user_input.lower() == "/mode":
                new_mode = "proxy" if client.mode == "backend" else "backend"
                client.mode = new_mode
                print(f"{GREEN}✓ Đã chuyển sang chế độ: {new_mode.upper()}{RESET}\n")
                continue

            if user_input.lower() == "/help":
                print(f"""
{BOLD}CÁC LỆNH HỖ TRỢ:{RESET}
  {CYAN}/new, /reset{RESET}  : Bắt đầu phiên hội thoại mới (xóa conversationId hiện tại).
  {CYAN}/mode{RESET}         : Chuyển đổi giữa chế độ BACKEND (chuẩn hệ thống) và PROXY (trực tiếp LLM).
  {CYAN}/info{RESET}         : Xem thông tin cấu hình hiện tại (URL, Conversation ID, Token).
  {CYAN}/exit, quit{RESET}   : Thoát chương trình.
""")
                continue

            if user_input.lower() == "/info":
                print(f"""
{BOLD}THÔNG TIN HỆ THỐNG:{RESET}
  • Backend URL: {client.backend_url}
  • Proxy URL: {client.proxy_url}
  • User Email: {client.user_email}
  • Conversation ID: {client.conversation_id}
  • Current Mode: {client.mode}
""")
                continue

            print(f"{DIM}Đang gửi yêu cầu tới AI... (xin chờ){RESET}", end="\r", flush=True)
            if client.mode == "backend":
                start_t = time.time()
                res = client.chat_backend(user_input)
                dur = time.time() - start_t
                print(" " * 50, end="\r")  # clear line
                display_backend_response(res)
                print(f"{DIM}[Thời gian xử lý: {dur:.2f}s | ConvID: {client.conversation_id}]{RESET}\n")
            else:
                start_t = time.time()
                res = client.chat_proxy(user_input)
                dur = time.time() - start_t
                print(" " * 50, end="\r")  # clear line
                if res.get("error"):
                    print(f"{RED}❌ Lỗi gọi Proxy: {res.get('exception')}{RESET}\n")
                else:
                    print(f"\n{BOLD}{GREEN}🤖 DANASEA AI (Proxy Direct):{RESET}\n{res.get('message')}\n")
                    print(f"{DIM}[Thời gian xử lý: {dur:.2f}s | Model: {res.get('model')}]{RESET}\n")

        except (KeyboardInterrupt, EOFError):
            print(f"\n{CYAN}Đã ngắt phiên làm việc. Tạm biệt!{RESET}")
            break


if __name__ == "__main__":
    main()
