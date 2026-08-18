#!/usr/bin/env bash
# Генерирует ключ и самоподписанный сертификат для подписи плагина.
# Marketplace принимает только подписанные сборки; ключ создаётся один раз
# и переиспользуется для всех последующих версий — не теряйте его.
#
# Использование: tools/generate-signing-key.sh [каталог]
# По умолчанию — ~/.env-datasources-signing (вне репозитория, чтобы не утёк в git).

set -euo pipefail

out_dir="${1:-$HOME/.env-datasources-signing}"
mkdir -p "$out_dir"
chmod 700 "$out_dir"

if [ -f "$out_dir/private.pem" ]; then
    echo "В $out_dir уже есть private.pem — перезаписывать не буду." >&2
    echo "Подписывать новые версии нужно тем же ключом, иначе IDE не примет обновление." >&2
    exit 1
fi

read -r -s -p "Пароль для приватного ключа: " key_password; echo
read -r -s -p "Повторите: " key_password_repeat; echo
[ "$key_password" = "$key_password_repeat" ] || { echo "Пароли не совпадают." >&2; exit 1; }

openssl genpkey -aes-256-cbc -algorithm RSA \
    -out "$out_dir/private_encrypted.pem" -pkeyopt rsa_keygen_bits:4096 \
    -pass pass:"$key_password"

openssl pkcs8 -topk8 -inform PEM -outform PEM \
    -in "$out_dir/private_encrypted.pem" -out "$out_dir/private.pem" \
    -passin pass:"$key_password" -passout pass:"$key_password"

openssl req -new -x509 -key "$out_dir/private_encrypted.pem" \
    -sha256 -days 3650 -out "$out_dir/chain.crt" \
    -passin pass:"$key_password" \
    -subj "/CN=Env Data Sources/O=Env Data Sources/C=UZ"

chmod 600 "$out_dir"/*.pem "$out_dir"/chain.crt
unset key_password key_password_repeat

cat <<MSG

Готово. Файлы в $out_dir:
  private.pem  — приватный ключ (PKCS#8, под паролем)
  chain.crt    — сертификат

Перед публикацией выставьте окружение:

  export PRIVATE_KEY="\$(cat $out_dir/private.pem)"
  export CERTIFICATE_CHAIN="\$(cat $out_dir/chain.crt)"
  export PRIVATE_KEY_PASSWORD='<пароль, который вы ввели>'
  export PUBLISH_TOKEN='<токен из profile → Marketplace → API tokens>'

Каталог лежит вне репозитория — держите его в бэкапе и не коммитьте.
MSG
