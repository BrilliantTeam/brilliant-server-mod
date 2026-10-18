# Brilliant-Server-Mod

輝煌伺服器（`brilliantw.net`）的專屬客戶端模組，只在連線至輝煌時啟用，不安裝也能正常遊玩。

## 功能

- **Discord 遊玩狀態**：顯示所在分流與遊玩時間，切換分流自動更新
- **聊天欄選單**：可搜尋，收錄公告、官網文章、聊天紀錄、常用訊息、頻道設定、自訂指令、分享座標、展示物品與動作
- **頻道分頁與私訊視窗**：多選只看特定頻道；私訊視窗可縮放並顯示頭像
- **自動腳本**：Scratch 式積木編輯器，事件、偵測、操作、清單與自訂積木，內建範本庫與動畫教學
- **指令別名與 TAB 補全**：為自訂指令設定別名，模組內的指令輸入框都能 TAB 補全
- **表情符號選單**、**彩色文字預覽**、**高清看圖**、**配方書修正**、**遊戲內更新通知**

## 安裝

1. 安裝 [Fabric Loader](https://fabricmc.net/use/installer/) 與 [Fabric API](https://modrinth.com/mod/fabric-api)
2. 把對應自己遊戲版本的 jar 放進 `.minecraft/mods/`（支援 1.21.11、26.1 ~ 26.3）
3. 需要 Discord 遊玩狀態時，在背景開啟 Discord 桌面版

## 網路與資料

- 連線至官網取得公告、文章、圖片、玩家頭像與線上人數，並以玩家名稱查詢所在分流
- 請求會附上模組與遊戲版本；官網只以雜湊方式記錄玩家名稱，用於版本使用統計
- 設定存於 `config/brilliant-server-mod-*.json`，腳本紀錄檔存於 `config/brilliant-server-mod-logs/`
- 聊天、私訊與圖片只暫存在記憶體

圖示取自 [Pixelarticons](https://github.com/halfmage/pixelarticons)（MIT）。
