# Recording 

## Android 扫描二维码 socket链接pc端传输ui录屏信息
- Api:Zxing 开源二维码扫描框架
- Api：eventBus 事件传递消息机制
- Android-Api:DataDindding MvvM 绑定数据 （bindfike注册框架存在apt编译冲突）

## 使用场景：
- Android 客户端扫描Pc端提供的二维码 获取Pc端的Socket地址信息 客户端发送Socket 通知Pc端 录屏所客户端的Wifi 链接 ip（注：客户端和Pc端必须同在一个局域网内属于一对一链接（UDP--广播形式实现一对多的链接）） pc端链接客户端wifiIp 播放录屏图片帧
## 实现原理：
- 调用MediaProjectionManager


