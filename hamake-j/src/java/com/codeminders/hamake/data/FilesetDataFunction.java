package com.codeminders.hamake.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.codeminders.hamake.Context;
import com.codeminders.hamake.Utils;

public class FilesetDataFunction extends DataFunction {

	public enum Variant {
		LIST, MASK;

		public static Variant parseString(String variant) {
			if (StringUtils.isEmpty(variant))
				return null;
			if (variant.equalsIgnoreCase("mask"))
				return MASK;
			else
				return LIST;
		}
	}

	public static final Log LOG = LogFactory.getLog(FilesetDataFunction.class);

	private String path;
	private String mask;

	public FilesetDataFunction(String id, int generation, long validityPeriod,
			String workFolder, String path, String mask) throws IOException {
		super(id, generation, validityPeriod, workFolder);
		this.path = path;
		this.mask = mask;
	}

	@Override
	public boolean clear(Context context) throws IOException {
		Path path = toPath(context);
		if (getFileSystem(context, path).exists(path))
			return getFileSystem(context, path).delete(path, true);
		return false;
	}

	@Override
	public FileSystem getFileSystem(Context context, Path path)
			throws IOException {
		return toPath(context)
				.getFileSystem(
						context
								.getHamake(Context.HAMAKE_PROPERTY_HADOOP_CONFIGURATION) != null ? (Configuration) context
								.getHamake(Context.HAMAKE_PROPERTY_HADOOP_CONFIGURATION)
								: new Configuration());
	}

	@Override
	public List<Path> getPath(Context context, Object... arguments)
			throws IOException {

		Path path = toPath(context);
		FileSystem fs = getFileSystem(context, path);
		if (!fs.exists(path) || !fs.getFileStatus(path).isDir()) {
			throw new IOException("Folder " + path
					+ " should exist and be a folder");
		}
		List<Path> filesList = new ArrayList<Path>();
		Boolean create = false;
		Variant variant = Variant.LIST;
		if (arguments.length == 1 && arguments[0] instanceof Boolean)
			create = (Boolean) arguments[0];
		if (arguments.length == 1 && arguments[0] instanceof String)
			variant = Variant.parseString((String) arguments[0]);

		boolean exists = fs.exists(path);
		if (!exists) {
			if (create) {
				LOG.info("Creating " + path);
				fs.mkdirs(path);
			} else {
				LOG.error("Path " + this + " does not exist!");
			}
			return Collections.emptyList();
		}

		if (variant == Variant.LIST) {
			FileStatus[] list = fs.listStatus(path);

			for (FileStatus s : list) {
				Path p = new Path(s.getPath().toUri().getPath());
				if (Utils.matches(p, mask))
					filesList.add(p);
			}
		} else if (variant == Variant.MASK) {
			if (mask != null) {
				return Arrays.asList(new Path(path.toString() + "/" + mask));
			}
		}

		return filesList;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof FilesetDataFunction))
			return false;
		else {
			FilesetDataFunction that = (FilesetDataFunction) obj;
			if (that.path.equals(path) && that.mask.equals(mask))
				return true;
		}
		return false;
	}

	@Override
	public long getMinTimeStamp(Context context) throws IOException {
		FileSystem fs = getFileSystem(context, null);
		long modificationTime = Long.MAX_VALUE;
		for (Path p : getPath(context)) {
			if (!fs.exists(p))
				continue;

			FileStatus stat = fs.getFileStatus(p);

			if (stat.getModificationTime() < modificationTime) {
				modificationTime = stat.getModificationTime();
			}
		}
		return modificationTime;
	}

	@Override
	public boolean intersects(Context context, DataFunction that)
			throws IOException {
		if (that instanceof FilesetDataFunction) {
			String thatParentPath = ((FilesetDataFunction) that)
					.toPath(context).toString();
			String thisParentPath = this.toPath(context).toString();
			return (matches(thatParentPath, thisParentPath) || matches(
					thisParentPath, thatParentPath))
					&& getGeneration() >= that.getGeneration()
					&& ((FilesetDataFunction) that).mask.equals(this.mask);
		} else
			return super.intersects(context, that);
	}

	private Path toPath(Context context) throws IOException {
		String processedPath = Utils.replaceVariables(context, this.path);
		Path path = new Path(processedPath);
		if (!path.isAbsolute() && !StringUtils.isEmpty(getWorkFolder()))
			path = new Path(getWorkFolder(), processedPath);

		return path;
	}

}
