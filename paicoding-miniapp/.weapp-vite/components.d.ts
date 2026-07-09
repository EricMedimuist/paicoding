/* eslint-disable */
// biome-ignore lint: disable
// oxlint-disable
// ------
// 由 weapp-vite autoImportComponents 生成
import type { ComponentOptionsMixin, DefineComponent, PublicProps, WeappIntrinsicElementBaseAttributes } from 'wevu'
import type { ComponentProp } from 'weapp-vite/typed-components'

export {}

type WeappComponent<Props = Record<string, any>> = new (...args: any[]) => InstanceType<DefineComponent<{}, {}, {}, {}, {}, ComponentOptionsMixin, ComponentOptionsMixin, {}, string, PublicProps, Props & WeappIntrinsicElementBaseAttributes, {}>>
type __WeappComponentProps<TComponent> = TComponent extends new (...args: any[]) => { $props: infer Props } ? Props : Record<string, any>
type __WeappComponentImport<TModule, Fallback = {}> = 0 extends 1 & TModule ? Fallback : TModule extends { default: infer Component } ? Component extends new (...args: infer Args) => infer Instance ? new (...args: Args) => Omit<Instance, '$props'> & { $props: __WeappComponentProps<Component> & __WeappComponentProps<Fallback> } : Fallback : Fallback

declare module 'wevu' {
  export interface GlobalComponents {
    TAvatar: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/avatar/avatar"), WeappComponent<ComponentProp<"t-avatar">>>;
    't-avatar': __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/avatar/avatar"), WeappComponent<ComponentProp<"t-avatar">>>;
    TButton: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/button/button"), WeappComponent<ComponentProp<"t-button">>>;
    't-button': __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/button/button"), WeappComponent<ComponentProp<"t-button">>>;
    TIcon: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/icon/icon"), WeappComponent<ComponentProp<"t-icon">>>;
    't-icon': __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/icon/icon"), WeappComponent<ComponentProp<"t-icon">>>;
    TImage: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/image/image"), WeappComponent<ComponentProp<"t-image">>>;
    't-image': __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/image/image"), WeappComponent<ComponentProp<"t-image">>>;
    TInput: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/input/input"), WeappComponent<ComponentProp<"t-input">>>;
    't-input': __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/input/input"), WeappComponent<ComponentProp<"t-input">>>;
    TNavbar: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/navbar/navbar"), WeappComponent<ComponentProp<"t-navbar">>>;
    't-navbar': __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/navbar/navbar"), WeappComponent<ComponentProp<"t-navbar">>>;
  }
}

// 用于 TSX 支持
declare global {
  const TAvatar: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/avatar/avatar"), WeappComponent<ComponentProp<"t-avatar">>>
  const TButton: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/button/button"), WeappComponent<ComponentProp<"t-button">>>
  const TIcon: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/icon/icon"), WeappComponent<ComponentProp<"t-icon">>>
  const TImage: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/image/image"), WeappComponent<ComponentProp<"t-image">>>
  const TInput: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/input/input"), WeappComponent<ComponentProp<"t-input">>>
  const TNavbar: __WeappComponentImport<typeof import("tdesign-miniprogram/miniprogram_dist/navbar/navbar"), WeappComponent<ComponentProp<"t-navbar">>>
}
