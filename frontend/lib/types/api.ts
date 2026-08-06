export type Department = {
  departmentId: string;
  code: string;
  name: string;
  category: string | null;
  description: string | null;
  status: string;
};

export type Doctor = {
  doctorId: string;
  code: string;
  name: string;
  title: string | null;
  specialty: string | null;
  status: string;
  department: { departmentId: string; name: string };
};

export type DoctorDetail = Doctor & {
  intro: string | null;
  consultationFee: number;
};

export type ScheduleSlot = {
  slotId: string;
  scheduleDate: string;
  periodCode: string;
  startTime: string | null;
  endTime: string | null;
  totalQuota: number;
  availableQuota: number;
  fee: number;
  status: string;
  department: { departmentId: string; name: string };
  doctor: { doctorId: string; name: string; title: string | null };
};

export type Appointment = {
  appointmentId: string;
  appointmentNo: string;
  status: string;
  bookedAt: string;
  scheduleDate: string;
  periodCode: string;
  departmentName: string;
  doctorName: string;
  fee: number;
};

export type AppointmentCreated = {
  appointmentId: string;
  appointmentNo: string;
  status: string;
};

export type AppointmentCancelled = {
  appointmentId: string;
  status: string;
  cancelledAt: string;
  cancelReason: string | null;
};

export type Conversation = {
  conversationId: string;
  title: string;
  sceneType: string;
  status: string;
  lastMessageAt: string | null;
};

export type ChatMessage = {
  messageId: string;
  role: "USER" | "ASSISTANT";
  messageType: string;
  content: string;
  citations: string;
  createdAt: string;
  pending?: boolean;
};

export type ChatCitation = {
  sourceId?: string;
  title?: string;
  snippet?: string;
  index?: number;
};

export type ToolStatusData = {
  toolName?: string;
  status?: string;
  message?: string;
};

export type CurrentUser = {
  userId: string;
  displayName: string;
  mobileMasked: string | null;
  emailMasked: string | null;
  defaultHospitalCode: string | null;
  defaultDepartment: { departmentId: string; name: string } | null;
  roles: string[];
};

export type LoginResult = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: {
    userId: string;
    displayName: string;
    mobileMasked: string | null;
    roles: string[];
  };
};

export type RegisterResult = {
  userId: string;
  displayName: string;
};
