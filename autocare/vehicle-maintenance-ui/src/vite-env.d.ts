/// <reference types="vite/client" />

/** Augment Vite env for Docker-built UI image. */
interface ImportMetaEnv {
  /** ISO UTC image build stamp (Docker build-arg VITE_BUILD_TIME). */
  readonly VITE_BUILD_TIME?: string;
}
