package com.hiveworkshop.blizzard.casc.vfs;

import com.hiveworkshop.blizzard.casc.Key;

/**
 * A reference to part of a file in CASC storage.
 */
public class StorageReference {
	/**
	 * Logical offset of this chunk.
	 */
	private long offset = 0;

	/**
	 * Logical size of this chunk.
	 */
	private long size = 0;

	/**
	 * Encoding key of chunk.
	 */
	private Key encodingKey = null;

	/**
	 * Physical size of stored data banks.
	 */
	private long physicalSize = 0;

	/**
	 * Member that purpose is currently not know. Known values are 0x00 and 0x0A.
	 */
	private byte unknownMember1 = 0;

	/**
	 * Logical size of data banks.
	 */
	private long actualSize = 0;

	public StorageReference(final long offset, final long size, Key encodingKey, final int physicalSize,
			final byte unknownMember1, final int actualSize) {
		this.offset = offset;
		this.size = size;
		this.encodingKey = encodingKey;
		this.physicalSize = physicalSize;
		this.unknownMember1 = unknownMember1;
		this.actualSize = actualSize;
	}

	@Override
	public String toString() {
		final var builder = new StringBuilder();
		builder.append("FileReference{encodingKey=");
		builder.append(encodingKey);
		builder.append(", offset=");
		builder.append(offset);
		builder.append(", size=");
		builder.append(size);
		builder.append(", physicalSize=");
		builder.append(physicalSize);
		builder.append(", unknownMember1=");
		builder.append(unknownMember1);
		builder.append(", actualSize=");
		builder.append(actualSize);
		builder.append("}");

		return builder.toString();
	}

	/**
	 * Get the offset of this chunk.
	 * <p>
	 * The exact mechanic of this is speculative as the only observed value is 0.
	 * 
	 * @return Offset in bytes
	 */
	public long getOffset() {
		return offset;
	}

	/**
	 * Get the size of this chunk.
	 * <p>
	 * The exact mechanic of this is speculative as the only observed value is
	 * identical to actual size.
	 * 
	 * @return Size in bytes.
	 */
	public long getSize() {
		return size;
	}

	public Key getEncodingKey() {
		return encodingKey;
	}

	/**
	 * Get the total physical size of the associated data banks. This is the size
	 * occupied by the data in the CASC local storage.
	 * 
	 * @return Size in bytes.
	 */
	public long getPhysicalSize() {
		return physicalSize;
	}

	/**
	 * This is a temporary method used to help diagnose the purpose of the member.
	 * It should not be used in production code.
	 * 
	 * @return Value of unknown member 1.
	 */
	public byte getUnknownMember1() {
		return unknownMember1;
	}

	/**
	 * Get the total logical size of the associated data banks. This is the size
	 * occupied by the data once expanded from local storage.
	 * <p>
	 * It should match chunk size as otherwise there would unaccounted bytes.
	 * 
	 * @return Size in bytes.
	 */
	public long getActualSize() {
		return actualSize;
	}

}
