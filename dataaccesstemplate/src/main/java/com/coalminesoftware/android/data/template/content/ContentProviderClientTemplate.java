package com.coalminesoftware.android.data.template.content;

import java.util.List;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;

import com.coalminesoftware.android.data.template.BaseClientTemplate;
import com.coalminesoftware.android.data.template.mapping.ContentValuesMapper;
import com.coalminesoftware.android.data.template.mapping.RowCallbackHandler;
import com.coalminesoftware.android.data.template.mapping.RowMapper;

import static com.coalminesoftware.android.util.DeprecationUtil.hasAndroidN;

/**
 * Providers a simpler API for developers to interact with {@link ContentProvider}s, eliminating
 * much of the boilerplate code.  The design also encouraging developers to write self-contained,
 * reusable {@link RowMapper}s and {@link RowCallbackHandler}s for building business objects from
 * Cursor rows, and {@link ContentValuesMapper}s for generating {@link ContentValues} from business
 * objects.
 */
public class ContentProviderClientTemplate extends BaseClientTemplate {
	private ContentProviderQuerier providerQuerier;

	/**
	 * Constructs a template using a {@link ContentResolver}, to which <code>insert()</code>,
	 * <code>query()</code> and <code>update()</code> calls are delegated.  Templates constructed
	 * in this fashion will incur the same performance penalty that developers can expect from
	 * calling those methods themselves on a <code>ContentResolver</code>, compared to acquiring a
	 * {@link ContentProviderClient} from a resolver and calling the client's corresponding methods
	 * instead.  However, templates constructed with a resolver have the advantage of not requiring
	 * any cleanup and not being limited to interacting with a single provider.
	 */
	public ContentProviderClientTemplate(ContentResolver contentResolver) {
		providerQuerier = new ContentResolverQuerier(contentResolver);
	}

	/**
	 * Constructs a template using a {@link ContentProviderClient}, to which <code>insert()</code>,
	 * <code>query()</code> and <code>update()</code> calls are delegated.  Because the use of a
	 * client avoids repeated {@link ContentProvider} lookups  and permission checks, developers
	 * can expect to see better performance when constructing a template in this fashion.
	 * <p>
	 * However, a client (and a template constructed with a client) can only insert/query/update
	 * URI's handled by the provider for which the client was acquired. Clients also need to be
	 * released when no longer needed, by calling {@link ContentProviderClient#release()}.  For
	 * convenience, users can also call {@link #closeClient()} on a ContentProviderClientTemplate
	 * constructed with a ContentProviderClient.
	 */
	public ContentProviderClientTemplate(ContentProviderClient providerClient) {
		providerQuerier = new ContentProviderClientQuerier(providerClient);
	}

	public <RowModel> RowModel query(Uri uri, String[] projection, RowMapper<RowModel> rowMapper) {
		return query(uri, projection, null, null, null, rowMapper);
	}

	public <RowModel> RowModel query(Uri uri, String[] projection, String selectClause, String[] selectionArguments, String sortOrder, RowMapper<RowModel> rowMapper) {
		Cursor cursor = null;
		try {
			cursor = providerQuerier.query(uri, projection, selectClause, selectionArguments, sortOrder);
			return mapRow(cursor, rowMapper);
		} finally {
			if(cursor != null) {
				cursor.close();
			}
		}
	}

	public <RowModel> List<RowModel> queryForList(Uri uri, String[] projection, RowMapper<RowModel> rowMapper) {
		return queryForList(uri, projection, null, null, null, rowMapper);
	}

	public <RowModel> List<RowModel> queryForList(Uri uri, String[] projection, String selectClause, String[] selectionArguments, String sortOrder, RowMapper<RowModel> rowMapper) {
		Cursor cursor = null;
		try {
			cursor = providerQuerier.query(uri, projection, selectClause, selectionArguments, sortOrder);
			return mapRows(cursor, rowMapper);
		} finally {
			if(cursor != null) {
				cursor.close();
			}
		}
	}

	public void query(Uri uri, String[] projection, RowCallbackHandler callbackHandler) {
		query(uri, projection, null, null, null, callbackHandler);
	}

	public void query(Uri uri, String[] projection, String selectClause, String[] selectionArguments, String sortOrder, RowCallbackHandler callbackHandler) {
		Cursor cursor = null;
		try {
			cursor = providerQuerier.query(uri, projection, selectClause, selectionArguments, sortOrder);
			processRows(cursor, callbackHandler);
		} finally {
			if(cursor != null) {
				cursor.close();
			}
		}
	}

	/**
	 * Inserts a record into the given URI, using the given mapper to generate the ContentValues.
	 * 
	 * @return The URI of the inserted record, as reported by the ContentProvider being inserted into.
	 */
	public <RowModel> Uri insert(Uri uri, RowModel rowObject, ContentValuesMapper<RowModel> mapper) {
		return providerQuerier.insert(uri, mapper.mapContentValues(rowObject));
	}

	/**
	 * Updates the given URI, using the given mapper to generate the ContentValues.
	 * 
	 * @return The number of rows affected by the update.
	 */
	public <RowModel> int update(Uri uri, RowModel rowObject, ContentValuesMapper<RowModel> mapper, String whereClause, String[] selectionArguments) {
		return providerQuerier.update(uri, mapper.mapContentValues(rowObject), whereClause, selectionArguments);
	}

	/**
	 * Updates the given URI, using the given mapper to generate the ContentValues.
	 * 
	 * @return The number of rows affected by the update.
	 */
	public <RowModel> int update(Uri uri, RowModel rowObject, ContentValuesMapper<RowModel> mapper) {
		return update(uri, rowObject, mapper, null, null);
	}

	/**
	 * Deletes the record(s) for the given URI.
	 * 
	 * @return The number of rows affected by the update.
	 */
	public int delete(Uri uri, String whereClause, String[] selectionArguments) {
		return providerQuerier.delete(uri, whereClause, selectionArguments);
	}

	/**
	 * For templates that were constructed with a provider client, this convenience method releases
	 * the client once it is no longer needed, by calling {@link ContentProviderClient#close()} on
	 * Android 24+, or {@link ContentProviderClient#release()} on previous Android releases.
	 * 
	 * @throws IllegalStateException Thrown if the template does not wrap a ContentProviderClient.
	 * @see ContentProviderClientTemplate#ContentProviderClientTemplate(ContentProviderClient) */
	public void closeClient() {
		if(providerQuerier instanceof ContentProviderClientQuerier) {
			close(((ContentProviderClientQuerier)providerQuerier).mProviderClient);
		} else {
			throw new IllegalStateException("Template was not constructed with ContentProviderClient to release.");
		}
	}

	@SuppressWarnings("deprecation")
	private static void close(ContentProviderClient client) {
		if(hasAndroidN()) {
			client.close();
		} else {
			client.release();
		}
	}


	private interface ContentProviderQuerier {
		Uri insert(Uri uri, ContentValues contentValues);
		Cursor query(Uri uri, String[] projection, String selectClause, String[] selectionArguments, String sortOrder);
		int update(Uri uri, ContentValues contentValues, String whereClause, String[] selectionArguments);
		int delete(Uri uri, String whereClause, String[] selectionArguments);
	}

	private static class ContentResolverQuerier implements ContentProviderQuerier {
		ContentResolver mContentResolver;

		public ContentResolverQuerier(ContentResolver contentResolver) {
			if(contentResolver == null) {
				throw new IllegalArgumentException("A resolver is required.");
			}

			mContentResolver = contentResolver;
		}

		@Override
		public Uri insert(Uri uri, ContentValues contentValues) {
			return mContentResolver.insert(uri, contentValues);
		}

		@Override
		public Cursor query(Uri uri, String[] projection, String selectClause, String[] selectionArguments, String sortOrder) {
			return mContentResolver.query(uri, projection, selectClause, selectionArguments, sortOrder);
		}

		@Override
		public int update(Uri uri, ContentValues contentValues, String whereClause, String[] selectionArguments) {
			return mContentResolver.update(uri, contentValues, whereClause, selectionArguments);
		}

		@Override
		public int delete(Uri uri, String whereClause, String[] selectionArguments) {
			return mContentResolver.delete(uri, whereClause, selectionArguments);
		}
	}

	private static class ContentProviderClientQuerier implements ContentProviderQuerier {
		ContentProviderClient mProviderClient;

		public ContentProviderClientQuerier(ContentProviderClient providerClient) {
			if(providerClient == null) {
				throw new IllegalArgumentException("A client is required.");
			}

			mProviderClient = providerClient;
		}

		@Override
		public Uri insert(Uri uri, ContentValues contentValues) {
			try {
				return mProviderClient.insert(uri, contentValues);
			} catch (RemoteException e) {
				throw new RuntimeException("Underying ContentProviderClient could not insert values.", e);
			}
		}

		@Override
		public Cursor query(Uri uri, String[] projection, String selectClause, String[] selectionArguments, String sortOrder) {
			try {
				return mProviderClient.query(uri, projection, selectClause, selectionArguments, sortOrder);
			} catch (RemoteException e) {
				throw new RuntimeException("Underying ContentProviderClient could not be queried.", e);
			}
		}

		@Override
		public int update(Uri uri, ContentValues contentValues, String whereClause, String[] selectionArguments) {
			try {
				return mProviderClient.update(uri, contentValues, whereClause, selectionArguments);
			} catch (RemoteException e) {
				throw new RuntimeException("Underying ContentProviderClient could not update values.", e);
			}
		}

		@Override
		public int delete(Uri uri, String whereClause, String[] selectionArguments) {
			try {
				return mProviderClient.delete(uri, whereClause, selectionArguments);
			} catch (RemoteException e) {
				throw new RuntimeException("Underying ContentProviderClient could not delete the record.", e);
			}
		}
	}
}
