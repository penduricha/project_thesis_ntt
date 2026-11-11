#!/usr/bin/env bash
#
# Bootstrap Python 3.14.4 + virtual environment + HTTPX + Jupyter
# Usage:
#   chmod +x install_py_venv.sh
#   ./install_py_venv.sh
#
# Optional:
#   PYTHON_BIN=/usr/bin/python3.14 VENV_DIR=.venv ./install_py_venv.sh

set -Eeuo pipefail

# Run from the directory containing this script, so the venv is created
# inside the project folder regardless of the caller's current directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PYTHON_BIN="${PYTHON_BIN:-python3.14}"
VENV_DIR="${VENV_DIR:-.venv}"
REQUIRED_VERSION="3.14.4"

info()  { printf '\\n[INFO] %s\\n' "$*"; }
error() { printf '\\n[ERROR] %s\\n' "$*" >&2; }

# Check interpreter availability.
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
    error "Không tìm thấy $PYTHON_BIN."
    cat >&2 <<'EOF'
Hãy cài Python 3.14 và module venv trước, sau đó chạy lại script.
Trên Ubuntu, thử:
  sudo apt update
  sudo apt install python3.14 python3.14-venv python3.14-dev

Nếu Ubuntu chưa có gói Python 3.14, hãy cài Python 3.14.4 từ nguồn
phân phối Python đáng tin cậy; script này không tự ý thêm PPA bên thứ ba.
EOF
    exit 1
fi

ACTUAL_VERSION="$("$PYTHON_BIN" -c 'import platform; print(platform.python_version())')"
if [[ "$ACTUAL_VERSION" != "$REQUIRED_VERSION" ]]; then
    error "Cần Python $REQUIRED_VERSION, nhưng $PYTHON_BIN đang là $ACTUAL_VERSION."
    error "Cài đúng Python $REQUIRED_VERSION hoặc đặt PYTHON_BIN=/duong/dan/toi/python3.14.4."
    exit 1
fi

info "Python $ACTUAL_VERSION: OK"

# Ensure the venv module is available before attempting creation.
if ! "$PYTHON_BIN" -m venv --help >/dev/null 2>&1; then
    error "Python venv chưa được cài đặt."
    error "Trên Ubuntu, chạy: sudo apt install python3.14-venv"
    exit 1
fi

if [[ -e "$VENV_DIR" && ! -d "$VENV_DIR" ]]; then
    error "$VENV_DIR đã tồn tại nhưng không phải thư mục."
    exit 1
fi

if [[ -d "$VENV_DIR" && ! -x "$VENV_DIR/bin/python" ]]; then
    error "$VENV_DIR đã tồn tại nhưng không phải virtual environment hợp lệ."
    error "Hãy đổi VENV_DIR hoặc đổi tên/xóa thư mục đó rồi chạy lại."
    exit 1
fi

if [[ ! -d "$VENV_DIR" ]]; then
    info "Tạo virtual environment tại: $SCRIPT_DIR/$VENV_DIR"
    "$PYTHON_BIN" -m venv "$VENV_DIR"
else
    info "Sử dụng virtual environment hiện có: $SCRIPT_DIR/$VENV_DIR"
fi

VENV_PYTHON="$SCRIPT_DIR/$VENV_DIR/bin/python"

info "Nâng cấp pip, setuptools và wheel..."
"$VENV_PYTHON" -m pip install --upgrade pip setuptools wheel

info "Cài HTTPX và bộ công cụ Jupyter Notebook/JupyterLab..."
"$VENV_PYTHON" -m pip install --upgrade \
    httpx \
    notebook \
    jupyterlab \
    ipykernel

info "Đăng ký kernel cho Jupyter..."
"$VENV_PYTHON" -m ipykernel install \
    --sys-prefix \
    --name python314-project \
    --display-name "Python 3.14.4 (.venv)"

info "Kiểm tra phiên bản và import..."
"$VENV_PYTHON" - <<'PY'
import sys
import httpx
import IPython
import ipykernel
import notebook
import jupyterlab

print("Python      :", sys.version.split()[0])
print("Executable  :", sys.executable)
print("HTTPX       :", httpx.__version__)
print("IPython     :", IPython.__version__)
print("ipykernel   :", ipykernel.__version__)
print("Notebook    :", notebook.__version__)
print("JupyterLab  :", jupyterlab.__version__)
print("All imports : OK")
PY

cat <<EOF

============================================================
CÀI ĐẶT HOÀN TẤT
============================================================
Virtual environment: $SCRIPT_DIR/$VENV_DIR

Kích hoạt:
  source "$VENV_DIR/bin/activate"

Mở Jupyter Notebook:
  jupyter notebook

Hoặc mở JupyterLab:
  jupyter lab

Thoát virtual environment:
  deactivate

Lưu ý:
  - Mỗi lần mở terminal mới, cần chạy lại lệnh activate.
  - Chọn kernel "Python 3.14.4 (.venv)" trong giao diện Jupyter.
  - Không cần sudo khi pip install bên trong virtual environment.
============================================================
EOF