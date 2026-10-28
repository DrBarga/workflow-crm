// Иконки макета входа. Линейные, 24×24 viewBox, цвет берут из currentColor.
import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Icon({ size = 15, children, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      {...props}
    >
      {children}
    </svg>
  );
}

export function EyeIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0" />
      <path d="M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6" />
    </Icon>
  );
}

export function EyeOffIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M10.585 10.587a2 2 0 0 0 2.829 2.828" />
      <path d="M16.681 16.673a8.717 8.717 0 0 1 -4.681 1.327c-3.6 0 -6.6 -2 -9 -6c1.272 -2.12 2.712 -3.678 4.32 -4.674m2.86 -1.146a9.055 9.055 0 0 1 1.82 -.18c3.6 0 6.6 2 9 6c-.666 1.11 -1.379 2.067 -2.138 2.87" />
      <path d="M3 3l18 18" />
    </Icon>
  );
}

export function AlertIcon(props: IconProps) {
  return (
    <Icon size={16} {...props}>
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
      <path d="M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0 -18" />
    </Icon>
  );
}

export function ShieldIcon(props: IconProps) {
  return (
    <Icon size={14} {...props}>
      <path d="M12 3l7 4v5c0 4 -3 7 -7 9c-4 -2 -7 -5 -7 -9v-5z" />
      <path d="M12 11v3" />
    </Icon>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <Icon size={10} strokeWidth={3} {...props}>
      <path d="M5 12l5 5l9 -9" />
    </Icon>
  );
}

export function SpinnerIcon(props: IconProps) {
  return (
    <Icon size={16} {...props}>
      <path d="M12 3a9 9 0 1 0 9 9" />
    </Icon>
  );
}

export function ClockIcon(props: IconProps) {
  return (
    <Icon size={14} {...props}>
      <path d="M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0 -18" />
      <path d="M12 7v5l3 2" />
    </Icon>
  );
}

export function DeviceIcon(props: IconProps) {
  return (
    <Icon size={13} {...props}>
      <path d="M4 5h16v10H4z" />
      <path d="M8 19h8" />
    </Icon>
  );
}
