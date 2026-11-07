import type {CodegenTypes, HostComponent, ViewProps} from 'react-native';
import {codegenNativeComponent} from 'react-native';

type PressEvent = Readonly<{text: string}>;

export interface NativeProps extends ViewProps {
  text?: string;
  onPress?: CodegenTypes.BubblingEventHandler<PressEvent> | null;
}

export default codegenNativeComponent<NativeProps>(
  'ItgDemoNativeView',
) as HostComponent<NativeProps>;
