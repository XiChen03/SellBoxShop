# GitHub 推送说明（SellBoxShop）

本文记录本机推送 `SellBoxShop` 到 GitHub 的固定步骤，下次直接照着做即可。

## 仓库信息

| 项目 | 值 |
|---|---|
| 本地目录 | `C:\AI\sellbox` |
| GitHub 仓库 | https://github.com/XiChen03/SellBoxShop |
| 默认分支 | `main` |
| SSH 地址 | `git@github.com:XiChen03/SellBoxShop.git` |
| SSH 私钥 | `C:\Users\Administrator\.ssh\id_ed25519` |
| SSH 公钥 | `C:\Users\Administrator\.ssh\id_ed25519.pub` |

> 私钥**不要**发给任何人，也不要提交到仓库。只需要把 `.pub` 公钥添加到 GitHub。

## 当前公钥

```text
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIBu71iP2JyKenPp0qvBo8IZyhCh6jd7k3DENdJqnpHp8 XiChen03@users.noreply.github.com
```

## 一、首次配置（换电脑时才需要）

### 1. 生成密钥

在 PowerShell 或 Git Bash 执行：

```bash
ssh-keygen -t ed25519 -C "XiChen03@users.noreply.github.com" -f "$HOME/.ssh/id_ed25519" -N ""
```

Windows CMD 里没有 `$HOME`，可以直接写完整路径：

```bat
ssh-keygen -t ed25519 -C "XiChen03@users.noreply.github.com" -f C:\Users\Administrator\.ssh\id_ed25519 -N ""
```

### 2. 复制公钥

```powershell
Get-Content $HOME\.ssh\id_ed25519.pub | Set-Clipboard
```

或：

```bat
type C:\Users\Administrator\.ssh\id_ed25519.pub | clip
```

### 3. 添加到 GitHub

打开：

https://github.com/settings/ssh/new

- Title：随便填，比如 `SellBox-PC`
- Key：粘贴公钥
- 点 **Add SSH key**

每台电脑生成自己的密钥，各自添加一次即可，不要共用私钥。

### 4. 验证

```bash
ssh -T git@github.com
```

成功会显示：

```text
Hi XiChen03! You've successfully authenticated, but GitHub does not provide shell access.
```

如果提示 `Permission denied (publickey)`，说明公钥没加到 GitHub，或密钥文件名不是默认的 `id_ed25519`。

## 二、日常推送

```bash
cd C:\AI\sellbox

# 查看改了什么
git status
git diff

# 添加并提交
git add -A
git commit -m "说明这次改了什么"

# 推送
git push
```

首次推送如果还没有 upstream：

```bash
git push -u origin main
```

## 三、remote 配置

当前 remote 应保持为 SSH：

```bash
git remote -v
```

应显示：

```text
origin  git@github.com:XiChen03/SellBoxShop.git (fetch)
origin  git@github.com:XiChen03/SellBoxShop.git (push)
```

如果不是，改回来：

```bash
git remote set-url origin git@github.com:XiChen03/SellBoxShop.git
```

## 四、提交身份

本仓库已配置：

```bash
git config user.name XiChen03
git config user.email 53290665+XiChen03@users.noreply.github.com
```

换电脑后如果 `git commit` 提示 `Author identity unknown`，执行上面两条即可。

## 五、常见问题

### 1. `Permission denied (publickey)`

检查：

```bash
ls $HOME/.ssh/id_ed25519*
ssh -T git@github.com
```

确认：

- `id_ed25519` 和 `id_ed25519.pub` 都存在
- GitHub 的 **Settings → SSH and GPG keys** 里有这台机器的公钥
- remote 是 `git@github.com:...`，不是 `https://...`

### 2. 连接 GitHub 超时

本机可用本地代理 `127.0.0.1:7890` 访问 GitHub HTTPS。SSH 通常可直连；如果 SSH 也超时，可在 `C:\Users\Administrator\.ssh\config` 加：

```text
Host github.com
    HostName github.com
    User git
    IdentityFile C:\Users\Administrator\.ssh\id_ed25519
```

### 3. 想换一台电脑

不要复制旧私钥。新电脑重新生成一对密钥，把新的 `.pub` 加到 GitHub；旧电脑的 key 可以保留，也可以之后在 GitHub 删除。

### 4. 私钥泄露

立即到 GitHub 删除对应公钥，然后重新生成密钥并添加新的公钥。

## 六、只推送源码，不推送构建产物

仓库 `.gitignore` 已忽略：

- `build/`
- `.gradle/`
- `latest.log`
- 其它构建缓存

提交前看一眼：

```bash
git status
```

如果看到 `build/`、`run/`、`latest.log`，不要 `git add -A`，先确认 `.gitignore` 生效。
