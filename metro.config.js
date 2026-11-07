const path = require('path');

const modules = process.env.ITG_RN_NODE_MODULES
  ? path.resolve(process.env.ITG_RN_NODE_MODULES)
  : path.join(__dirname, 'node_modules');
const {getDefaultConfig, mergeConfig} = require(
  require.resolve('@react-native/metro-config', {paths: [modules]}),
);

module.exports = mergeConfig(getDefaultConfig(__dirname), {
  watchFolders: [modules],
  resolver: {nodeModulesPaths: [modules]},
});
