import React, {useState} from 'react';
import {AppRegistry, NativeModules, Pressable, StyleSheet, Text, View} from 'react-native';
import ItgDemoNativeView from './specs/ItgDemoNativeComponent';
import NativeItgDemo from './specs/NativeItgDemo';

function NativeDemoPage() {
  const [calculation, setCalculation] = useState('点击计算 6 × 7');
  const [pressed, setPressed] = useState('点击下面的原生 View');

  const multiply = async () => {
    if (!NativeItgDemo) {
      setCalculation('ItgDemoTurboModule 未注册');
      return;
    }
    try {
      setCalculation(`TurboModule 返回：${await NativeItgDemo.multiply(6, 7)}`);
    } catch (error) {
      setCalculation(`调用失败：${String(error)}`);
    }
  };

  return (
    <View style={styles.root}>
      <Text style={styles.title}>Fabric + TurboModule 示例</Text>
      <ItgDemoNativeView
        text="来自 Android TextView 的 Fabric 组件"
        onPress={event => setPressed(`原生点击：${event.nativeEvent.text}`)}
        style={styles.nativeView}
      />
      <Text>{pressed}</Text>
      <Pressable accessibilityRole="button" onPress={multiply} style={styles.button}>
        <Text style={styles.buttonText}>调用 TurboModule.multiply</Text>
      </Pressable>
      <Text>{calculation}</Text>
    </View>
  );
}

function BusinessPage({instanceId, pageId, params}) {
  if (pageId === 'nativeDemo') {
    return <NativeDemoPage />;
  }
  const close = async () => {
    try {
      await NativeModules.ItgRnBridge.closeContainer(
        instanceId,
        JSON.stringify({orderId: params?.orderId ?? null, closed: true}),
      );
    } catch (error) {
      console.error('Cannot close RN container', error);
    }
  };

  return (
    <View style={styles.root}>
      <Text style={styles.title}>订单详情</Text>
      <Text>页面：{pageId}</Text>
      <Text>订单号：{params?.orderId ?? '—'}</Text>
      <Pressable accessibilityRole="button" onPress={close} style={styles.button}>
        <Text style={styles.buttonText}>关闭并返回结果</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24},
  title: {fontSize: 24, fontWeight: '600', marginBottom: 20},
  button: {backgroundColor: '#2059c5', padding: 14, borderRadius: 8, marginTop: 24},
  buttonText: {color: 'white'},
  nativeView: {width: '100%', height: 68, marginBottom: 16},
});

AppRegistry.registerComponent('ItgRnBusiness', () => BusinessPage);
