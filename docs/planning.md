# 🌟 LightEdit – 项目开发计划（真实进度版｜DDL 调整后）

> ⚠ **说明**  
> 由于课程官方将作业截止时间延长至 **12/10**，原计划中的扩展功能（动画过渡 / 滤镜 / 贴纸）从 12/4 前的高压力窗口被顺延至 12/8–12/10 实现。  
>
> 我在 **12/4 按原计划完成了全部基础功能**（裁剪、旋转、亮度、对比度、文字、保存）。  
>
> **12/8–12/10** 完成扩展功能 1–3（动画过渡、滤镜、贴纸），最后完成工程化优化并打标签 v1.0.0。

---

# 🗂️ 0. 项目阶段总览（实际进度）

| 阶段 | 日期 | 阶段目标 | 实际进度 |
|------|-------|------------|----------------|
| Phase 1 | 11/24 | 工程初始化 | ✔ 完成 |
| Phase 2 | 11/25–11/28 | 相册模块 | ✔ 完成 |
| Phase 3 | 11/29 | 相机模块 | ✔ 完成 |
| Phase 4 | 11/30–12/01 | 编辑器核心（裁剪/旋转/缩放） | ✔ 完成 |
| Phase 5 | 12/02 | 亮度/对比度 | ✔ 完成 |
| Phase 6 | 12/03–12/04 | 基础编辑功能 + 保存模块 | ✔ 原DDL前全部完成 v1.1.0|
| ⬇ DDL 延长 | — | 扩展功能顺延到 12/8–12/10 | — |
| Phase 7 | 12/08 | 扩展功能 1：相册→预览动画过渡 | ✔ 完成 |
| Phase 8 | 12/09 | 扩展功能 2：滤镜（6 种） | ✔ 完成 |
| Phase 9 | 12/10 | 扩展功能 3：贴纸模块 | ✔ 完成 |
| Phase 10 | 12/10 | 最终优化 + 文档 + Tag | ✔ v2.3.0 |

---

# 📌 Phase 1：工程初始化（11/24）✔ 完成

## 内容摘要
- GitHub 仓库创建  
- main / develop 分支  
- Git Flow 建立  
- docs/ 文档体系建立  
- GitHub Project 看板  
- 四大文档（README / planning / requirements / git-workflow）

## Review
- 工程结构清晰  
- 版本控制规范，为后续开发提供稳定基础  

---

# 📌 Phase 2：相册模块（11/25–11/28）✔ 完成

## 功能
- MediaStore 扫描全部照片  
- 全部照片页 + 文件夹页  
- 文件夹内照片页  
- 缩略图异步加载（正方形裁剪）  
- 大图预览  

## 关键技术
- ContentResolver  
- RecyclerView 优化  
- 权限处理  
- 专用缩略图加载器（ThumbnailLoader）
- 保证图片可拖动不超出屏幕并添加动画

---

# 📌 Phase 3：相机模块（11/29）✔ 完成

## 内容
- CameraX Preview + ImageCapture  
- 拍照文件写出  
- 自动跳转到 EditorFragment  
- 相机权限检测  

## Review
- 功能稳定  
- 无相机设备有兼容方案

---

# 📌 Phase 4：编辑器核心（11/30–12/1）✔ 完成

## 实现
- ZoomableImageView（捏合缩放 / 拖动）  
- 裁剪框（自由 + 比例模式）  
- 固定比例裁剪支持：1:1 / 4:3 / 9:16 / 16:9 / 3:4  
- 旋转（±90° / 180°）  
- 水平翻转、垂直翻转  

## Review
- 手势流畅  
- 裁剪结果精确，无畸变
- 保证图片可拖动不超出屏幕并添加动画

---

# 📌 Phase 5：亮度/对比度（12/02）✔ 完成

## 内容
- ColorMatrix 亮度调节  
- ColorMatrix 对比度调节  
- 实时预览  
- “按住对比原图”模式（长按显示原图对比效果）

## Review
- 结合行业类似软件，添加长按对比功能实现
- 
---

# 📌 Phase 6：基础编辑功能 + 保存模块（12/03–12/04）✔ 原DDL前全部完成

## 实现的完整功能链路
- 文字模块：  
  - 拖动、缩放、旋转  
  - 字体 / 字号 / 颜色 / 透明度  
- 文字编辑 UX 完全独立  
- 图片 + 文字合成  
- 保存到系统相册  
- 自动水印“训练营”  
- 全流程测试：相册 → 预览 → 编辑 → 保存 并逐级返回

## 核心说明
**按照原定截止日期 12/4 前，所有必做功能已完成且可运行。**

---

# 🟦 12/08–12/10：扩展功能阶段（DDL 延期后执行）

# 📌 Phase 7（扩展 1）：相册 → 预览动画过渡（12/08）✔ 完成

## 内容
- Shared Element Transition  
- 缩略图 → 大图平滑放大  
- postponeEnterTransition / startPostponedEnterTransition 控制时机  
- 防止预览背景提前挡住动画

## Debug Highlights
- 解决预览背景挡住动画过程
- 从编辑回到预览控件不显示

---

# 📌 Phase 8（扩展 2）：滤镜（6 种）（12/09）✔ 完成

## 滤镜类型
- ORIGINAL（原图）  
- BLACK_WHITE  
- VINTAGE  
- FRESH  
- WARM  
- COOL  

全部通过 **手动 ColorMatrix** 实现。

## UX
- 底部统一结构：“取消 – 滤镜 – 确认”  
- 与裁剪 / 调色 UI 风格完全一致

## Debug Highlights  

---

# 📌 Phase 9（扩展 3）：贴纸模块（12/10）✔ 完成

## 内容
- StickerOverlayView（全新手势系统）
- 与 TextOverlayView 完全一致的交互体验：
  - 贴纸边框
  - 左下角 +1（复制）  
  - 右上角 删除  
  - 右下角 缩放 + 旋转  
- 贴纸下采样加载，避免 OOM  
- 贴纸层级管理  
  - bringToFront  
  - sendToBack  
  - moveForward  
  - moveBackward  

## Debug Highlights
- 修复贴纸中心定位错误（非 PNG 边缘问题）  
- 修复贴纸过大导致的崩溃（下采样解决）  
- 统一贴纸与文字的手势与 UX  

---

# 📌 Phase 10：最终优化 + 交付（12/10）✔ 完成

## 内容
- 全流程回归测试  
- 内存回收（bitmap.recycle 安全处理）  
- 代码结构优化（分层结构 editor / overlay / view）  
- 提交文档（UML、架构图、总结）  
- Git 打 Tag：**v2.3.0**

---

# 🧾 最终 Review：工程化总结

## ✔ 工程化实践
- Git Flow（main / develop / feature）  
- PR 合并与代码审查  
- 多文档体系（planning / README / architecture / UML）  

## ✔ 技术成果
- 自定义 View （Zoomable / TextOverlay / StickerOverlay）  
- 手势系统（单指拖动，双指缩放旋转）  
- Matrix + ColorMatrix 熟练应用  
- Shared Element Animation  
- Bitmap + Canvas 合成  
- MediaStore 持久化写入  

## ✔ 自身成长
- 完整走过一个可交付 Android App 的工程化流程  
- 理解大型编辑 App 的架构拆分方式  
- 学会自定义 View、手势系统、多模块协同开发    

---

# 🔁 文档更新（Rolling Update）

本 planning.md 持续更新，包含：
- 阶段回顾  
- Debug 记录  
- Backlog 调整  
- 提交历史  
