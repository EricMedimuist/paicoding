/* eslint-disable */
// @ts-nocheck
// biome-ignore lint: disable
// oxlint-disable
// ------
// 由 weapp-vite 自动生成，请勿编辑。
declare module 'weapp-vite/typed-components' {
  export interface ComponentProps {
    't-avatar': {
      readonly alt?: string;
      readonly badgeProps?: BadgeProps;
      readonly bordered?: boolean;
      readonly hideOnLoadFailed?: boolean;
      readonly icon?: string | object;
      readonly image?: string;
      readonly imageProps?: ImageProps;
      readonly shape?: ShapeEnum;
      readonly size?: string;
    };
    't-button': {
      readonly activityType?: number;
      readonly appParameter?: string;
      readonly block?: boolean;
      readonly content?: string;
      readonly customDataset?: null;
      readonly disabled?: boolean;
      readonly entrancePath?: string;
      readonly ghost?: boolean;
      readonly hoverClass?: string;
      readonly hoverStartTime?: number;
      readonly hoverStayTime?: number;
      readonly hoverStopPropagation?: boolean;
      readonly icon?: string | object;
      readonly lang?: 'en' | 'zh_CN' | 'zh_TW';
      readonly loading?: boolean;
      readonly loadingProps?: LoadingProps;
      readonly needShowEntrance?: boolean;
      readonly openType?: 'contact' | 'liveActivity' | 'share' | 'getPhoneNumber' | 'getRealtimePhoneNumber' | 'getUserInfo' | 'launchApp' | 'openSetting' | 'feedback' | 'chooseAvatar' | 'agreePrivacyAuthorization' | 'phoneOneClickLogin';
      readonly phoneNumberNoQuotaToast?: boolean;
      readonly sendMessageImg?: string;
      readonly sendMessagePath?: string;
      readonly sendMessageTitle?: string;
      readonly sessionFrom?: string;
      readonly shape?: 'rectangle' | 'square' | 'round' | 'circle';
      readonly showMessageCard?: boolean;
      readonly size?: 'extra-small' | 'small' | 'medium' | 'large';
      readonly theme?: 'default' | 'primary' | 'danger' | 'light';
      readonly tId?: string;
      readonly type?: 'submit' | 'reset';
      readonly variant?: 'base' | 'outline' | 'dashed' | 'text';
    };
    't-icon': {
      readonly color?: string;
      readonly name?: string;
      readonly prefix?: string;
      readonly size?: string | number;
    };
    't-image': {
      readonly error?: string;
      readonly height?: string | number;
      readonly lazy?: boolean;
      readonly loading?: string;
      readonly mode?: 'scaleToFill' | 'aspectFit' | 'aspectFill' | 'widthFix' | 'heightFix' | 'top' | 'bottom' | 'center' | 'left' | 'right' | 'top left' | 'top right' | 'bottom left' | 'bottom right';
      readonly shape?: 'circle' | 'round' | 'square';
      readonly showMenuByLongpress?: boolean;
      readonly src?: string;
      readonly tId?: string;
      readonly webp?: boolean;
      readonly width?: string | number;
    };
    't-input': {
      readonly adjustPosition?: boolean;
      readonly align?: 'left' | 'center' | 'right';
      readonly allowInputOverMax?: boolean;
      readonly alwaysEmbed?: boolean;
      readonly autoFocus?: boolean;
      readonly borderless?: boolean;
      readonly clearable?: boolean | object;
      readonly clearTrigger?: 'always' | 'focus';
      readonly confirmHold?: boolean;
      readonly confirmType?: 'send' | 'search' | 'next' | 'go' | 'done';
      readonly cursor?: number;
      readonly cursorColor?: string;
      readonly cursorSpacing?: number;
      readonly disabled?: boolean;
      readonly focus?: boolean;
      readonly format?: InputFormatType;
      readonly holdKeyboard?: boolean;
      readonly label?: string;
      readonly layout?: 'vertical' | 'horizontal';
      readonly maxcharacter?: number;
      readonly maxlength?: number;
      readonly placeholder?: string;
      readonly placeholderClass?: string;
      readonly placeholderStyle?: string;
      readonly prefixIcon?: string | object;
      readonly readonly?: boolean;
      readonly safePasswordCertPath?: string;
      readonly safePasswordCustomHash?: string;
      readonly safePasswordLength?: number;
      readonly safePasswordNonce?: string;
      readonly safePasswordSalt?: string;
      readonly safePasswordTimeStamp?: number;
      readonly selectionEnd?: number;
      readonly selectionStart?: number;
      readonly status?: 'default' | 'success' | 'warning' | 'error';
      readonly suffix?: string;
      readonly suffixIcon?: string | object;
      readonly tips?: string;
      readonly type?: 'text' | 'number' | 'idcard' | 'digit' | 'safe-password' | 'password' | 'nickname';
      readonly value?: InputValue;
    };
    't-navbar': {
      readonly animation?: boolean;
      readonly delta?: number;
      readonly fixed?: boolean;
      readonly isHiddenInSpecialScene?: boolean;
      readonly leftArrow?: boolean;
      readonly placeholder?: boolean;
      readonly safeAreaInsetTop?: boolean;
      readonly title?: string;
      readonly titleMaxLength?: number;
      readonly visible?: boolean;
      readonly zIndex?: number;
    };
  }
  export type ComponentPropName = keyof ComponentProps;
  export type ComponentProp<Name extends string> = Name extends ComponentPropName ? ComponentProps[Name] : Record<string, any>;
  export const componentProps: ComponentProps;
}
