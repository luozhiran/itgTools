import React from 'react';
import {AppRegistry, NativeModules, Pressable, StyleSheet, Text, View} from 'react-native';

function BusinessPage({instanceId, pageId, params}) {
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
});

AppRegistry.registerComponent('ItgRnBusiness', () => BusinessPage);
